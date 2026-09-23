package com.xiaoa.admin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoa.admin.dto.CreateContentPackageRequest;
import com.xiaoa.admin.mapper.ContentPackageMapper;
import com.xiaoa.admin.model.ContentPackage;
import com.xiaoa.common.auth.AdminPermissionService;
import com.xiaoa.common.auth.AuthPrincipal;
import com.xiaoa.common.exception.BusinessException;
import com.xiaoa.common.exception.ErrorCode;
import com.xiaoa.task.mapper.TaskMapper;
import com.xiaoa.task.model.Task;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Service
public class ContentPackageService {

    private final ContentPackageMapper packageMapper;
    private final TaskMapper taskMapper;
    private final AdminPermissionService permissionService;
    private final ObjectMapper objectMapper;

    public ContentPackageService(ContentPackageMapper packageMapper, TaskMapper taskMapper,
                                 AdminPermissionService permissionService, ObjectMapper objectMapper) {
        this.packageMapper = packageMapper;
        this.taskMapper = taskMapper;
        this.permissionService = permissionService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ContentPackage create(CreateContentPackageRequest request) {
        AuthPrincipal principal = permissionService.requiredWrite();
        permissionService.requireHeadquarters();
        ContentPackage contentPackage = new ContentPackage();
        contentPackage.setTenantId(principal.getTenantId());
        contentPackage.setName(request.getName());
        contentPackage.setCalendarDate(request.getCalendarDate());
        contentPackage.setPublishAt(request.getPublishAt());
        contentPackage.setCopyDirection(request.getCopyDirection());
        contentPackage.setTaskTemplate(request.getTaskTemplate());
        contentPackage.setCreatedBy(principal.getUserId());
        packageMapper.insert(contentPackage);
        return contentPackage;
    }

    public List<ContentPackage> list() {
        return packageMapper.findAll(permissionService.requiredRead().getTenantId());
    }

    @Transactional
    public void disable(Long id) {
        permissionService.requireHeadquarters();
        packageMapper.updateStatus(permissionService.required().getTenantId(), id, 2);
    }

    @Scheduled(cron = "0 * * * * ?")
    @Transactional
    public void publishDuePackages() {
        for (ContentPackage contentPackage : packageMapper.findDue(LocalDateTime.now())) {
            if (contentPackage.getTenantId() == null) {
                continue;
            }
            Task task = buildTask(contentPackage);
            taskMapper.insert(task);
            packageMapper.updateStatus(contentPackage.getTenantId(), contentPackage.getId(), 2);
        }
    }

    private Task buildTask(ContentPackage contentPackage) {
        Task task = new Task();
        task.setTenantId(contentPackage.getTenantId());
        task.setTitle(contentPackage.getName());
        task.setFormType(2);
        task.setContentPackageId(contentPackage.getId());
        task.setFrequency(1);
        task.setTargetScope(1);
        task.setTargetIds(toJson(Collections.emptyList()));
        task.setJudgeType(1);
        task.setCreatedBy(contentPackage.getCreatedBy());
        task.setCreatedLevel(1);
        task.setStatus(1);
        task.setStartAt(contentPackage.getPublishAt());
        return task;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "内容包任务模板生成失败");
        }
    }
}
