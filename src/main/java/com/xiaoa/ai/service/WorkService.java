package com.xiaoa.ai.service;

import com.xiaoa.ai.mapper.MediaTaskMapper;
import com.xiaoa.ai.model.MediaTask;
import com.xiaoa.ai.model.Work;
import com.xiaoa.ai.model.WorkPublishStatus;
import com.xiaoa.common.auth.AuthContext;
import com.xiaoa.common.auth.AuthPrincipal;
import com.xiaoa.common.exception.BusinessException;
import com.xiaoa.common.exception.ErrorCode;
import com.xiaoa.quota.service.QuotaService;
import com.xiaoa.task.mapper.AuditRecordMapper;
import com.xiaoa.task.mapper.WorkMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 作品改稿与详情：改文案（驳回态自动重提审）、重新生成媒体（全价扣费）。
 * 生成入口仍在 AiGatewayService，本服务只负责已有作品的后续操作。
 */
@Service
public class WorkService {

    private final WorkMapper workMapper;
    private final MediaTaskMapper mediaTaskMapper;
    private final AuditRecordMapper auditRecordMapper;
    private final WorkStateMachine stateMachine;
    private final QuotaService quotaService;
    private final AiGatewayService aiGatewayService;

    public WorkService(WorkMapper workMapper, MediaTaskMapper mediaTaskMapper,
                       AuditRecordMapper auditRecordMapper, WorkStateMachine stateMachine,
                       QuotaService quotaService, AiGatewayService aiGatewayService) {
        this.workMapper = workMapper;
        this.mediaTaskMapper = mediaTaskMapper;
        this.auditRecordMapper = auditRecordMapper;
        this.stateMachine = stateMachine;
        this.quotaService = quotaService;
        this.aiGatewayService = aiGatewayService;
    }

    public Work detail(Long workId) {
        Work work = requireOwned(workId);
        work.setAuditOpinion(null);
        com.xiaoa.task.model.AuditRecord audit = auditRecordMapper.findLatestDecided(work.getTenantId(), work.getId());
        if (audit != null) {
            work.setAuditOpinion(audit.getOpinion());
        }
        return work;
    }

    /**
     * 改文案：DRAFT/APPROVED/REJECTED 可改；REJECTED 改稿后自动重提审。
     */
    @Transactional
    public void updateCaption(Long workId, String caption) {
        Work work = requireOwned(workId);
        stateMachine.check(work, WorkPublishStatus.DRAFT, WorkPublishStatus.APPROVED, WorkPublishStatus.REJECTED);
        workMapper.updateCaption(work.getTenantId(), work.getId(), caption);
        if (WorkPublishStatus.REJECTED.equals(work.getPublishStatus())) {
            stateMachine.transit(work.getTenantId(), work.getId(),
                    WorkPublishStatus.REJECTED, WorkPublishStatus.PENDING_AUDIT);
        }
    }

    /**
     * 重新生成媒体：全价扣费，使用新幂等键 gen:{新任务ID}；旧成品地址保留在 media_task 历史。
     */
    @Transactional
    public Work regenerate(Long workId) {
        Work work = requireOwned(workId);
        stateMachine.check(work, WorkPublishStatus.DRAFT, WorkPublishStatus.APPROVED, WorkPublishStatus.REJECTED);
        MediaTask previous = work.getMediaTaskId() == null ? null : mediaTaskMapper.findById(work.getMediaTaskId());
        if (previous == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "作品缺少生成任务，无法重新生成");
        }
        MediaTask task = new MediaTask();
        task.setTenantId(work.getTenantId());
        task.setUserId(work.getUserId());
        task.setWorkId(work.getId());
        task.setStoreId(previous.getStoreId());
        task.setType(previous.getType());
        task.setScene(previous.getScene());
        task.setPrompt(previous.getPrompt());
        task.setCost(previous.getCost());
        mediaTaskMapper.insert(task);

        quotaService.charge(work.getTenantId(), previous.getStoreId(), previous.getCost(),
                "gen:" + task.getId(), "AI改稿重新生成扣费");

        if (workMapper.backToGenerating(work.getTenantId(), work.getId()) != 1) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "作品状态已变更，请刷新后重试");
        }
        workMapper.bindTask(work.getTenantId(), work.getId(), task.getId());
        aiGatewayService.dispatchAfterCommit(task.getId());
        return workMapper.findById(work.getId(), work.getTenantId());
    }

    private Work requireOwned(Long workId) {
        AuthPrincipal principal = AuthContext.required();
        if (workMapper.countOwned(principal.getTenantId(), workId, principal.getUserId()) == 0) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能操作自己的作品");
        }
        Work work = workMapper.findById(workId, principal.getTenantId());
        if (work == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "作品不存在");
        }
        return work;
    }
}
