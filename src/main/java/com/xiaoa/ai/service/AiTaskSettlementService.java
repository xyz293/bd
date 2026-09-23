package com.xiaoa.ai.service;

import com.xiaoa.ai.mapper.MediaTaskMapper;
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

    public AiTaskSettlementService(MediaTaskMapper mediaTaskMapper, WorkMapper workMapper,
                                   QuotaService quotaService) {
        this.mediaTaskMapper = mediaTaskMapper;
        this.workMapper = workMapper;
        this.quotaService = quotaService;
    }

    @Transactional
    public void success(MediaTask task, String contentUrl) {
        if (mediaTaskMapper.markSuccess(task.getId(), contentUrl) == 1) {
            workMapper.markSuccess(task.getTenantId(), task.getWorkId(), contentUrl);
        }
    }

    @Transactional
    public void failure(MediaTask task, String reason) {
        String safeReason = safeReason(reason);
        if (mediaTaskMapper.markFailed(task.getId(), safeReason) != 1) {
            return;
        }
        workMapper.markFailed(task.getTenantId(), task.getWorkId(), safeReason);
        quotaService.refundForAi(task.getTenantId(), task.getStoreId(), task.getCost(), task.getWorkId());
        mediaTaskMapper.markRefunded(task.getId());
    }

    private String safeReason(String reason) {
        return reason == null || reason.trim().isEmpty() ? "AI生成失败" : reason;
    }
}
