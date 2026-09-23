package com.xiaoa.task.service;

import com.xiaoa.common.auth.AuthContext;
import com.xiaoa.common.auth.AuthPrincipal;
import com.xiaoa.common.exception.BusinessException;
import com.xiaoa.common.exception.ErrorCode;
import com.xiaoa.task.dto.TaskBoardResponse;
import com.xiaoa.task.mapper.TaskRecordMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class TaskBoardService {

    private final TaskRecordMapper recordMapper;
    private final TaskService taskService;

    public TaskBoardService(TaskRecordMapper recordMapper, TaskService taskService) {
        this.recordMapper = recordMapper;
        this.taskService = taskService;
    }

    public TaskBoardResponse storeBoard(Long storeId, LocalDate periodDate) {
        AuthPrincipal principal = AuthContext.required();
        if (!"HQ_ADMIN".equals(principal.getRole()) && !"REGION_ADMIN".equals(principal.getRole())
                && !storeId.equals(principal.getOrgId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        taskService.ensureCurrentRecordsForStore(storeId, periodDate);
        long expected = recordMapper.countExpected(principal.getTenantId(), storeId, periodDate);
        long finished = recordMapper.countFinished(principal.getTenantId(), storeId, periodDate);
        return new TaskBoardResponse(storeId, periodDate, expected, finished,
                recordMapper.findByStorePeriodStatus(principal.getTenantId(), storeId, periodDate, 0));
    }
}
