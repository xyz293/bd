package com.xiaoa.task.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoa.common.auth.AuthContext;
import com.xiaoa.common.auth.AuthPrincipal;
import com.xiaoa.common.exception.BusinessException;
import com.xiaoa.common.exception.ErrorCode;
import com.xiaoa.task.dto.CreateTaskRequest;
import com.xiaoa.task.dto.TaskResponse;
import com.xiaoa.task.dto.UpdateTaskRequest;
import com.xiaoa.task.mapper.TaskMapper;
import com.xiaoa.task.mapper.TaskModifyLogMapper;
import com.xiaoa.task.mapper.TaskRecordMapper;
import com.xiaoa.tenant.mapper.UserOrgRoleMapper;
import com.xiaoa.tenant.model.UserOrgRole;
import com.xiaoa.task.model.Task;
import com.xiaoa.task.model.TaskModifyLog;
import com.xiaoa.task.model.TaskRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

@Service
public class TaskService {

    private final TaskMapper taskMapper;
    private final TaskModifyLogMapper modifyLogMapper;
    private final TaskRecordMapper recordMapper;
    private final TaskPermissionService permissionService;
    private final ObjectMapper objectMapper;
    private final UserOrgRoleMapper userOrgRoleMapper;

    public TaskService(TaskMapper taskMapper, TaskModifyLogMapper modifyLogMapper,
                       TaskRecordMapper recordMapper, TaskPermissionService permissionService,
                       ObjectMapper objectMapper, UserOrgRoleMapper userOrgRoleMapper) {
        this.taskMapper = taskMapper;
        this.modifyLogMapper = modifyLogMapper;
        this.recordMapper = recordMapper;
        this.permissionService = permissionService;
        this.objectMapper = objectMapper;
        this.userOrgRoleMapper = userOrgRoleMapper;
    }

    @Transactional
    public Task create(CreateTaskRequest request) {
        AuthPrincipal principal = AuthContext.required();
        permissionService.validateCreate(principal, request);
        validateDates(request.getStartAt(), request.getEndAt());
        if (request.getFormType() == 2 && request.getContentPackageId() == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "指定内容任务必须关联内容包");
        }
        Task task = new Task();
        task.setTenantId(principal.getTenantId());
        task.setTitle(request.getTitle());
        task.setFormType(request.getFormType());
        task.setContentPackageId(request.getContentPackageId());
        task.setPlatform(request.getPlatform());
        task.setFrequency(request.getFrequency());
        task.setTargetScope(request.getTargetScope());
        task.setTargetIds(toJson(permissionService.normalizeTargetIds(request)));
        task.setJudgeType(request.getJudgeType());
        task.setCreatedBy(principal.getUserId());
        task.setCreatedLevel(permissionService.levelOf(principal));
        task.setStatus(1);
        task.setStartAt(request.getStartAt());
        task.setEndAt(request.getEndAt());
        taskMapper.insert(task);
        preGenerateCurrentRecords(task, principal.getTenantId());
        return task;
    }

    /**
     * 系统建任务（内容包定时下发等无登录态场景）：跳过角色校验，按总部层级落库，
     * created_by=0 表示系统；模板合法性由调用方校验。
     */
    @Transactional
    public Task createBySystem(Long tenantId, String title, Integer formType, Long contentPackageId,
                               String platform, Integer frequency, Integer targetScope, List<Long> targetIds,
                               Integer judgeType, LocalDateTime endAt) {
        Task task = new Task();
        task.setTenantId(tenantId);
        task.setTitle(title);
        task.setFormType(formType == null ? 1 : formType);
        task.setContentPackageId(contentPackageId);
        task.setPlatform(platform);
        task.setFrequency(frequency);
        task.setTargetScope(targetScope == null ? 1 : targetScope);
        task.setTargetIds(toJson(targetIds == null ? new ArrayList<Long>() : targetIds));
        task.setJudgeType(judgeType == null ? 1 : judgeType);
        task.setCreatedBy(0L);
        task.setCreatedLevel(1);
        task.setStatus(1);
        task.setStartAt(LocalDateTime.now());
        task.setEndAt(endAt);
        taskMapper.insert(task);
        preGenerateCurrentRecords(task, tenantId);
        return task;
    }

    @Transactional
    public Task update(Long taskId, UpdateTaskRequest request) {
        AuthPrincipal principal = AuthContext.required();
        Task original = get(taskId);
        if (!permissionService.canModify(principal, original)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        validateDates(request.getStartAt(), request.getEndAt());
        if (request.getContentPackageId() == null && original.getFormType() == 2) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "指定内容任务必须关联内容包");
        }

        boolean storeVariant = "OWNER".equals(principal.getRole()) && original.getCreatedLevel() < 3;
        if (!storeVariant) {
            taskMapper.updateEditable(taskId, principal.getTenantId(), request.getTitle(),
                    request.getContentPackageId(), request.getPlatform(), request.getStartAt(), request.getEndAt());
            saveModifyLog(principal, original, original.getId(), request);
            return get(taskId);
        }

        Task variant = new Task();
        variant.setTenantId(principal.getTenantId());
        variant.setTitle(request.getTitle());
        variant.setFormType(original.getFormType());
        variant.setContentPackageId(request.getContentPackageId());
        variant.setPlatform(request.getPlatform());
        variant.setFrequency(original.getFrequency());
        variant.setTargetScope(3);
        variant.setTargetIds(toJson(java.util.Collections.singletonList(principal.getOrgId())));
        variant.setJudgeType(original.getJudgeType());
        variant.setSourceTaskId(original.getId());
        variant.setCreatedBy(principal.getUserId());
        variant.setCreatedLevel(3);
        variant.setStatus(1);
        variant.setStartAt(request.getStartAt());
        variant.setEndAt(request.getEndAt());
        taskMapper.insert(variant);
        saveModifyLog(principal, original, variant.getId(), request);
        preGenerateCurrentRecords(variant, principal.getTenantId());
        return variant;
    }

    private void saveModifyLog(AuthPrincipal principal, Task original, Long taskId, UpdateTaskRequest request) {
        TaskModifyLog log = new TaskModifyLog();
        log.setTenantId(principal.getTenantId());
        log.setTaskId(taskId);
        log.setModifiedBy(principal.getUserId());
        log.setChangeDetail(buildChangeDetail(original, request));
        modifyLogMapper.insert(log);
    }

    @Transactional
    public void updateStatus(Long taskId, Integer status) {
        AuthPrincipal principal = AuthContext.required();
        Task task = get(taskId);
        if (!permissionService.canModify(principal, task)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        taskMapper.updateStatus(taskId, principal.getTenantId(), status);
    }

    public Task get(Long taskId) {
        AuthPrincipal principal = AuthContext.required();
        Task task = taskMapper.findById(taskId, principal.getTenantId());
        if (task == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "任务不存在");
        }
        return task;
    }

    public List<Task> findAll() {
        AuthPrincipal principal = AuthContext.required();
        return taskMapper.findAll(principal.getTenantId());
    }

    public List<TaskResponse> myTasks() {
        AuthPrincipal principal = AuthContext.required();
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        List<Task> tasks = taskMapper.findActive(principal.getTenantId(), now);
        List<TaskResponse> result = new ArrayList<>();
        for (Task task : tasks) {
            if (!matches(task, principal) || hasStoreVariant(tasks, task, principal.getOrgId())) {
                continue;
            }
            LocalDate periodDate = periodDate(task.getFrequency(), today);
            ensureRecordForUser(task, principal, periodDate);
            TaskRecord record = recordMapper.findOne(principal.getTenantId(), task.getId(), principal.getUserId(), periodDate);
            result.add(new TaskResponse(task, record, periodDate));
        }
        return result;
    }

    public List<TaskModifyLog> modifyLogs(Long taskId) {
        AuthPrincipal principal = AuthContext.required();
        if (!"HQ_ADMIN".equals(principal.getRole()) && !"REGION_ADMIN".equals(principal.getRole())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        get(taskId);
        return modifyLogMapper.findByTask(principal.getTenantId(), taskId);
    }

    public LocalDate periodDate(Integer frequency, LocalDate date) {
        if (frequency == null || frequency == 1) {
            return date;
        }
        if (frequency == 2) {
            return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        }
        if (frequency == 3) {
            return date.withDayOfMonth(1);
        }
        throw new BusinessException(ErrorCode.INVALID_PARAMETER, "任务频率不正确");
    }

    private boolean hasStoreVariant(List<Task> tasks, Task task, Long storeId) {
        for (Task candidate : tasks) {
            if (task.getId().equals(candidate.getSourceTaskId()) && candidate.getTargetIds() != null
                    && candidate.getTargetIds().contains(String.valueOf(storeId))) {
                return true;
            }
        }
        return false;
    }

    @Transactional
    public void ensureCurrentRecordsForStore(Long storeId, LocalDate date) {
        AuthPrincipal current = AuthContext.required();
        LocalDate currentDate = date == null ? LocalDate.now() : date;
        for (Task task : taskMapper.findActive(current.getTenantId(), LocalDateTime.now())) {
            LocalDate currentPeriod = periodDate(task.getFrequency(), currentDate);
            for (UserOrgRole role : userOrgRoleMapper.findActiveByTenantId(current.getTenantId())) {
                if (!storeId.equals(role.getOrgId()) || (!"STAFF".equals(role.getRole()) && !"OWNER".equals(role.getRole()))) {
                    continue;
                }
                AuthPrincipal target = new AuthPrincipal(role.getUserId(), current.getTenantId(), role.getOrgId(),
                        role.getRole(), role.getDataScope());
                if (matches(task, target)) {
                    ensureRecordForUser(task, target, currentPeriod);
                }
            }
        }
    }

    private void ensureRecordForUser(Task task, AuthPrincipal principal, LocalDate periodDate) {
        TaskRecord record = new TaskRecord();
        record.setTenantId(principal.getTenantId());
        record.setTaskId(task.getId());
        record.setUserId(principal.getUserId());
        record.setStoreId(principal.getOrgId());
        record.setPeriodDate(periodDate);
        recordMapper.ensure(record);
    }

    private void preGenerateCurrentRecords(Task task, Long tenantId) {
        LocalDate date = LocalDate.now();
        LocalDate currentPeriod = periodDate(task.getFrequency(), date);
        for (UserOrgRole role : userOrgRoleMapper.findActiveByTenantId(tenantId)) {
            if (!"STAFF".equals(role.getRole()) && !"OWNER".equals(role.getRole())) {
                continue;
            }
            AuthPrincipal principal = new AuthPrincipal(role.getUserId(), tenantId, role.getOrgId(), role.getRole(), role.getDataScope());
            if (matches(task, principal)) {
                TaskRecord record = new TaskRecord();
                record.setTenantId(tenantId);
                record.setTaskId(task.getId());
                record.setUserId(role.getUserId());
                record.setStoreId(role.getOrgId());
                record.setPeriodDate(currentPeriod);
                recordMapper.ensure(record);
            }
        }
    }

    private boolean matches(Task task, AuthPrincipal principal) {
        if (task.getTargetScope() == 1) {
            return true;
        }
        List<Long> ids = parseIds(task.getTargetIds());
        if (task.getTargetScope() == 2) {
            for (UserOrgRole role : userOrgRoleMapper.findActiveByTenantId(principal.getTenantId())) {
                if (principal.getUserId().equals(role.getUserId()) && ids.contains(role.getOrgId())) {
                    return true;
                }
            }
            return false;
        }
        if (task.getTargetScope() == 3) {
            return ids.contains(principal.getOrgId());
        }
        if (task.getTargetScope() == 4) {
            return ids.contains(principal.getUserId());
        }
        return false;
    }

    private List<Long> parseIds(String json) {
        if (json == null || json.trim().isEmpty() || "[]".equals(json.trim())) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, objectMapper.getTypeFactory().constructCollectionType(List.class, Long.class));
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "任务目标范围格式错误");
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("任务目标范围序列化失败", exception);
        }
    }

    private String buildChangeDetail(Task original, UpdateTaskRequest request) {
        java.util.Map<String, Object> before = new java.util.LinkedHashMap<>();
        before.put("title", original.getTitle());
        before.put("contentPackageId", original.getContentPackageId());
        before.put("platform", original.getPlatform());
        before.put("startAt", original.getStartAt());
        before.put("endAt", original.getEndAt());
        java.util.Map<String, Object> after = new java.util.LinkedHashMap<>();
        after.put("title", request.getTitle());
        after.put("contentPackageId", request.getContentPackageId());
        after.put("platform", request.getPlatform());
        after.put("startAt", request.getStartAt());
        after.put("endAt", request.getEndAt());
        java.util.Map<String, Object> detail = new java.util.LinkedHashMap<>();
        detail.put("before", before);
        detail.put("after", after);
        return toJson(detail);
    }

    private void validateDates(LocalDateTime startAt, LocalDateTime endAt) {
        if (startAt != null && endAt != null && endAt.isBefore(startAt)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "任务结束时间不能早于开始时间");
        }
    }
}
