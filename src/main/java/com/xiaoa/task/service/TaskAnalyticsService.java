package com.xiaoa.task.service;

import com.xiaoa.common.auth.AdminDataScopeService;
import com.xiaoa.common.auth.AuthPrincipal;
import com.xiaoa.common.exception.BusinessException;
import com.xiaoa.common.exception.ErrorCode;
import com.xiaoa.task.mapper.AdminTaskMapper;
import com.xiaoa.task.model.AdminTaskBoardItem;
import com.xiaoa.task.model.AdminTaskBoardResponse;
import com.xiaoa.task.model.AdminTaskStoreItem;
import com.xiaoa.task.model.Task;
import com.xiaoa.task.model.TaskRecordDetail;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class TaskAnalyticsService {

    private final AdminTaskMapper taskMapper;
    private final TaskService taskService;
    private final AdminDataScopeService scopeService;

    public TaskAnalyticsService(AdminTaskMapper taskMapper, TaskService taskService,
                                AdminDataScopeService scopeService) {
        this.taskMapper = taskMapper;
        this.taskService = taskService;
        this.scopeService = scopeService;
    }

    public AdminTaskBoardResponse board(LocalDate requestedDate) {
        AuthPrincipal principal = scopeService.requiredTaskRead();
        LocalDate date = requestedDate == null ? LocalDate.now() : requestedDate;
        List<AdminTaskBoardItem> items = new ArrayList<>();
        for (Task task : taskService.findAll()) {
            if (!scopeService.canViewTask(principal, task)) {
                continue;
            }
            LocalDate period = taskService.periodDate(task.getFrequency(), date);
            Long regionId = regionId(principal);
            long expected = taskMapper.countExpectedScoped(principal.getTenantId(), task.getId(), period, null, regionId);
            long finished = taskMapper.countFinishedScoped(principal.getTenantId(), task.getId(), period, null, regionId);
            items.add(new AdminTaskBoardItem(task.getId(), task.getTitle(), task.getStatus(), task.getTargetScope(),
                    period, expected, finished));
        }
        return new AdminTaskBoardResponse(date, items);
    }

    public List<AdminTaskStoreItem> stores(Long taskId, LocalDate requestedDate) {
        AuthPrincipal principal = scopeService.requiredTaskRead();
        Task task = taskService.get(taskId);
        ensureTaskReadable(principal, task);
        LocalDate date = requestedDate == null ? LocalDate.now() : requestedDate;
        LocalDate period = taskService.periodDate(task.getFrequency(), date);
        return taskMapper.findStoreSummary(principal.getTenantId(), taskId, period, null, regionId(principal));
    }

    public List<TaskRecordDetail> recordDetails(Long taskId, Long storeId, LocalDate requestedDate) {
        AuthPrincipal principal = scopeService.requiredTaskRead();
        scopeService.requireStoreReadable(storeId);
        Task task = taskService.get(taskId);
        ensureTaskReadable(principal, task);
        LocalDate date = requestedDate == null ? LocalDate.now() : requestedDate;
        LocalDate period = taskService.periodDate(task.getFrequency(), date);
        taskService.ensureCurrentRecordsForStore(storeId, period);
        return taskMapper.findRecordDetails(principal.getTenantId(), taskId, period, storeId);
    }

    private void ensureTaskReadable(AuthPrincipal principal, Task task) {
        if (!principal.getTenantId().equals(task.getTenantId()) || !scopeService.canViewTask(principal, task)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权查看该任务数据");
        }
    }

    private Long regionId(AuthPrincipal principal) {
        return "REGION_ADMIN".equals(principal.getRole()) ? principal.getOrgId() : null;
    }
}
