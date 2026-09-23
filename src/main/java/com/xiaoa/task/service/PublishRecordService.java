package com.xiaoa.task.service;

import com.xiaoa.common.auth.AuthContext;
import com.xiaoa.common.auth.AuthPrincipal;
import com.xiaoa.common.exception.BusinessException;
import com.xiaoa.common.exception.ErrorCode;
import com.xiaoa.task.dto.PublishRecordRequest;
import com.xiaoa.task.mapper.PublishRecordMapper;
import com.xiaoa.task.mapper.TaskRecordMapper;
import com.xiaoa.task.mapper.WorkMapper;
import com.xiaoa.task.model.PublishRecord;
import com.xiaoa.task.model.Task;
import com.xiaoa.task.model.TaskRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
public class PublishRecordService {

    private final PublishRecordMapper publishRecordMapper;
    private final TaskRecordMapper taskRecordMapper;
    private final TaskService taskService;
    private final WorkMapper workMapper;

    public PublishRecordService(PublishRecordMapper publishRecordMapper, TaskRecordMapper taskRecordMapper,
                                TaskService taskService, WorkMapper workMapper) {
        this.publishRecordMapper = publishRecordMapper;
        this.taskRecordMapper = taskRecordMapper;
        this.taskService = taskService;
        this.workMapper = workMapper;
    }

    @Transactional
    public PublishRecord publish(PublishRecordRequest request) {
        AuthPrincipal principal = AuthContext.required();
        if (workMapper.countOwned(principal.getTenantId(), request.getWorkId(), principal.getUserId()) == 0) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能上报自己的作品");
        }
        Task task = request.getTaskId() == null ? null : taskService.get(request.getTaskId());
        if (task != null) {
            validateTask(task, principal, request);
            if (task.getJudgeType() != null && task.getJudgeType() == 2
                    && (request.getProofUrl() == null || request.getProofUrl().trim().isEmpty())) {
                throw new BusinessException(ErrorCode.INVALID_PARAMETER, "该任务需要上传截图凭证");
            }
        }
        PublishRecord record = new PublishRecord();
        record.setTenantId(principal.getTenantId());
        record.setWorkId(request.getWorkId());
        record.setUserId(principal.getUserId());
        record.setTaskId(request.getTaskId());
        record.setPlatform(request.getPlatform());
        record.setProofUrl(request.getProofUrl());
        publishRecordMapper.insert(record);
        if (task != null) {
            LocalDate periodDate = taskService.periodDate(task.getFrequency(), LocalDate.now());
            Long storeId = principal.getOrgId();
            TaskRecord taskRecord = new TaskRecord();
            taskRecord.setTenantId(principal.getTenantId());
            taskRecord.setTaskId(task.getId());
            taskRecord.setUserId(principal.getUserId());
            taskRecord.setStoreId(storeId);
            taskRecord.setPeriodDate(periodDate);
            taskRecordMapper.ensure(taskRecord);
            taskRecordMapper.complete(principal.getTenantId(), task.getId(), principal.getUserId(),
                    periodDate, record.getId(), LocalDateTime.now());
        }
        return record;
    }

    private void validateTask(Task task, AuthPrincipal principal, PublishRecordRequest request) {
        if (task.getStatus() == null || task.getStatus() != 1) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "任务已停用");
        }
        LocalDateTime now = LocalDateTime.now();
        if (task.getStartAt() != null && now.isBefore(task.getStartAt())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "任务尚未开始");
        }
        if (task.getEndAt() != null && now.isAfter(task.getEndAt())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "任务已结束");
        }
        if (task.getPlatform() != null && !task.getPlatform().equals(request.getPlatform())) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "发布平台与任务要求不一致");
        }
        if (!matchesTarget(task, principal)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "当前用户不在任务目标范围内");
        }
    }

    private boolean matchesTarget(Task task, AuthPrincipal principal) {
        if (task.getTargetScope() == null || task.getTargetScope() == 1) {
            return true;
        }
        String targetIds = task.getTargetIds();
        if (targetIds == null) {
            return false;
        }
        if (task.getTargetScope() == 3) {
            return targetIds.contains(String.valueOf(principal.getOrgId()));
        }
        if (task.getTargetScope() == 4) {
            return targetIds.contains(String.valueOf(principal.getUserId()));
        }
        return principal.getDataScope() != null && principal.getDataScope() <= 2;
    }
}
