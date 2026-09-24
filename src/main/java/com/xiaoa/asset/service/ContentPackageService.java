package com.xiaoa.asset.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoa.asset.dto.ContentPackageRequest;
import com.xiaoa.asset.dto.PackageTaskTemplate;
import com.xiaoa.asset.mapper.ContentPackageMapper;
import com.xiaoa.asset.model.ContentPackage;
import com.xiaoa.common.api.PageResult;
import com.xiaoa.common.auth.AdminPermissionService;
import com.xiaoa.common.auth.AuthPrincipal;
import com.xiaoa.common.exception.BusinessException;
import com.xiaoa.common.exception.ErrorCode;
import com.xiaoa.task.model.Task;
import com.xiaoa.task.service.TaskService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * 内容包配置与下发。下发策略：先条件更新抢占状态（防多实例双发），再建任务；
 * 建任务失败不回滚状态（不会重复下发），记 last_error 待人工补建。
 */
@Service
public class ContentPackageService {

    private static final DateTimeFormatter END_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int MAX_DUE = 50;

    private final ContentPackageMapper packageMapper;
    private final AdminPermissionService permissionService;
    private final TaskService taskService;
    private final ObjectMapper objectMapper;

    public ContentPackageService(ContentPackageMapper packageMapper, AdminPermissionService permissionService,
                                 TaskService taskService, ObjectMapper objectMapper) {
        this.packageMapper = packageMapper;
        this.permissionService = permissionService;
        this.taskService = taskService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ContentPackage create(ContentPackageRequest request) {
        AuthPrincipal principal = permissionService.required();
        permissionService.requireHeadquarters();
        if (request.getCalendarDate().isBefore(LocalDate.now())) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "营销日已过，不能创建内容包");
        }
        String taskTemplateJson = serialize(request.getTaskTemplate());
        PackageTaskTemplate template = parseTemplate(taskTemplateJson);
        validateTemplate(template, true);
        ContentPackage contentPackage = new ContentPackage();
        contentPackage.setTenantId(principal.getTenantId());
        contentPackage.setName(request.getName().trim());
        contentPackage.setCalendarDate(request.getCalendarDate());
        contentPackage.setPublishAt(request.getPublishAt());
        contentPackage.setCopyDirection(request.getCopyDirection());
        contentPackage.setTaskTemplate(taskTemplateJson);
        contentPackage.setStatus(ContentPackage.STATUS_ACTIVE);
        contentPackage.setCreatedBy(principal.getUserId());
        packageMapper.insert(contentPackage);
        return packageMapper.findById(principal.getTenantId(), contentPackage.getId());
    }

    public PageResult<ContentPackage> page(Integer status, int pageNo, int pageSize) {
        AuthPrincipal principal = permissionService.requiredRead();
        int size = Math.min(Math.max(pageSize, 1), 100);
        int offset = Math.max(pageNo, 1) - 1;
        List<ContentPackage> list = packageMapper.selectPage(principal.getTenantId(), status, offset * size, size);
        long total = packageMapper.countPage(principal.getTenantId(), status);
        return PageResult.of(list, total, Math.max(pageNo, 1), size);
    }

    /**
     * 撤销：ACTIVE → CANCELED 条件更新；已下发（DISPATCHED）不可撤销，走任务停用。
     */
    @Transactional
    public void cancel(Long id) {
        AuthPrincipal principal = permissionService.required();
        permissionService.requireHeadquarters();
        ContentPackage contentPackage = packageMapper.findById(principal.getTenantId(), id);
        if (contentPackage == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "内容包不存在");
        }
        if (packageMapper.cancel(principal.getTenantId(), id) != 1) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "内容包已下发或已撤销，无法撤销");
        }
    }

    /**
     * 下发建任务：模板宽松解析（历史种子模板缺省字段取默认值），任务创建 + 预生成周期记录。
     */
    @Transactional
    public Long dispatchPackage(ContentPackage contentPackage) {
        PackageTaskTemplate template = parseTemplate(contentPackage.getTaskTemplate());
        validateTemplate(template, false);
        Task task = taskService.createBySystem(contentPackage.getTenantId(),
                template.getTitle() == null ? contentPackage.getName() : template.getTitle(),
                template.getActionType(),
                contentPackage.getId(),
                template.getPlatform(),
                template.getFrequency(),
                template.getTargetScope() == null ? 1 : template.getTargetScope(),
                template.getTargetIds(),
                template.getJudgeType(),
                parseEndTime(template.getEndTime()));
        packageMapper.updateSourceTask(contentPackage.getId(), task.getId());
        return task.getId();
    }

    public List<ContentPackage> selectDue(LocalDateTime now) {
        return packageMapper.selectDue(now, MAX_DUE);
    }

    public int tryExpire(Long id) {
        return packageMapper.tryExpire(id);
    }

    public void markError(Long id, String message) {
        String safe = message == null ? "下发失败" : message;
        packageMapper.markError(id, safe.length() > 500 ? safe.substring(0, 500) : safe);
    }

    private void validateTemplate(PackageTaskTemplate template, boolean strict) {
        if (strict) {
            require(template.getActionType() != null && (template.getActionType() == 1 || template.getActionType() == 2),
                    "任务模板 actionType 必须为 1 或 2");
            require(template.getPlatform() != null && !template.getPlatform().trim().isEmpty(),
                    "任务模板 platform 不能为空");
            require(template.getJudgeType() != null && (template.getJudgeType() == 1 || template.getJudgeType() == 2),
                    "任务模板 judgeType 必须为 1 或 2");
            require(template.getEndTime() != null && !template.getEndTime().trim().isEmpty(),
                    "任务模板 endTime 不能为空");
        }
        require(template.getFrequency() != null && template.getFrequency() >= 1 && template.getFrequency() <= 3,
                "任务模板 frequency 必须为 1、2 或 3");
        if (template.getTargetScope() != null && template.getTargetScope() != 1) {
            require(template.getTargetIds() != null && !template.getTargetIds().isEmpty(),
                    "指定范围任务模板必须传入 targetIds");
        }
        parseEndTime(template.getEndTime());
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, message);
        }
    }

    private LocalDateTime parseEndTime(String endTime) {
        if (endTime == null || endTime.trim().isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(endTime.trim(), END_TIME_FORMAT);
        } catch (DateTimeParseException exception) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "任务模板 endTime 格式应为 yyyy-MM-dd HH:mm:ss");
        }
    }

    private PackageTaskTemplate parseTemplate(String json) {
        try {
            return objectMapper.readValue(json, PackageTaskTemplate.class);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "任务模板 JSON 格式错误");
        }
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "任务模板序列化失败");
        }
    }
}
