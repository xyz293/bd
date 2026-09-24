package com.xiaoa.ai.service;

import com.xiaoa.ai.mapper.MediaTaskMapper;
import com.xiaoa.ai.model.WorkPublishStatus;
import com.xiaoa.task.mapper.WorkMapper;
import com.xiaoa.ai.model.MediaTask;
import com.xiaoa.quota.service.QuotaService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiTaskSettlementService {

    private final MediaTaskMapper mediaTaskMapper;
    private final WorkMapper workMapper;
    private final QuotaService quotaService;
    private final AuditConfigService auditConfigService;
    private final CaptionService captionService;

    public AiTaskSettlementService(MediaTaskMapper mediaTaskMapper, WorkMapper workMapper,
                                   QuotaService quotaService, AuditConfigService auditConfigService,
                                   CaptionService captionService) {
        this.mediaTaskMapper = mediaTaskMapper;
        this.workMapper = workMapper;
        this.quotaService = quotaService;
        this.auditConfigService = auditConfigService;
        this.captionService = captionService;
    }

    /**
     * 生成成功结算：相当于 MediaTaskSuccessListener 的触发点。
     * 1) 按门店审核开关初始化发布状态（组织级覆盖品牌级 org_id=0）；
     * 2) 事务提交后异步补配套文案，不阻塞本事务。
     */
    @Transactional
    public void success(MediaTask task, String contentUrl) {
        if (mediaTaskMapper.markSuccess(task.getId(), contentUrl) == 1) {
            workMapper.markSuccess(task.getTenantId(), task.getWorkId(), contentUrl);
            boolean auditOn = auditConfigService.enabledFor(task.getTenantId(), task.getStoreId());
            workMapper.initPublishStatus(task.getTenantId(), task.getWorkId(),
                    auditOn ? WorkPublishStatus.PENDING_AUDIT : WorkPublishStatus.DRAFT);
            captionService.submitAfterCommit(task.getTenantId(), task.getWorkId());
        }
    }

    @Transactional
    public void failure(MediaTask task, String reason) {
        String safeReason = safeReason(reason);
        if (mediaTaskMapper.markFailed(task.getId(), safeReason) != 1) {
            return;
        }
        workMapper.markFailed(task.getTenantId(), task.getWorkId(), safeReason);
        quotaService.refundForAi(task.getTenantId(), task.getStoreId(), task.getCost(), task.getId());
        mediaTaskMapper.markRefunded(task.getId());
    }

    private String safeReason(String reason) {
        return reason == null || reason.trim().isEmpty() ? "AI生成失败" : reason;
    }
}
