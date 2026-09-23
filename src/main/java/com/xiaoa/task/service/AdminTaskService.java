package com.xiaoa.task.service;

import com.xiaoa.common.auth.AdminDataScopeService;
import com.xiaoa.common.auth.AuthPrincipal;
import com.xiaoa.task.dto.AdminTaskReportResponse;
import com.xiaoa.task.mapper.AdminTaskMapper;
import com.xiaoa.task.model.Task;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class AdminTaskService {

    private final AdminTaskMapper taskMapper;
    private final TaskService taskService;
    private final AdminDataScopeService scopeService;

    public AdminTaskService(AdminTaskMapper taskMapper, TaskService taskService,
                            AdminDataScopeService scopeService) {
        this.taskMapper = taskMapper;
        this.taskService = taskService;
        this.scopeService = scopeService;
    }

    public AdminTaskReportResponse report(Long taskId, Long storeId, LocalDate periodDate) {
        AuthPrincipal principal = scopeService.requiredTaskRead();
        Task task = taskService.get(taskId);
        if (!scopeService.canViewTask(principal, task)) {
            throw new com.xiaoa.common.exception.BusinessException(
                    com.xiaoa.common.exception.ErrorCode.FORBIDDEN, "无权查看该任务数据");
        }
        LocalDate date = periodDate == null ? LocalDate.now() : periodDate;
        LocalDate period = taskService.periodDate(task.getFrequency(), date);
        if (storeId != null) {
            scopeService.requireStoreReadable(storeId);
            taskService.ensureCurrentRecordsForStore(storeId, period);
        }
        Long regionId = "REGION_ADMIN".equals(principal.getRole()) ? principal.getOrgId() : null;
        long expected = taskMapper.countExpectedScoped(principal.getTenantId(), taskId, period, storeId, regionId);
        long finished = taskMapper.countFinishedScoped(principal.getTenantId(), taskId, period, storeId, regionId);
        return new AdminTaskReportResponse(taskId, period, expected, finished,
                taskMapper.findRecordsScoped(principal.getTenantId(), taskId, period, storeId, regionId));
    }
}
