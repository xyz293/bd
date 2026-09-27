package com.xiaoa.ai.chat.service;

import com.xiaoa.common.auth.AuthPrincipal;
import com.xiaoa.quota.service.QuotaService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 对话计费：首次出 3 版文案 1 点（幂等键 chat:{sessionId}:gen），
 * 微调/换版每次 1 点（幂等键 chat:{sessionId}:rev:{n}，n 递增防重）。
 * 扣费账户与 AI 生成一致：员工扣员工账户，无账户回退门店。
 */
@Service
public class ChatBillingService {

    private final QuotaService quotaService;
    private final long copyCost;

    public ChatBillingService(QuotaService quotaService,
                              @Value("${xiaoa.ai.cost.copy:1}") long copyCost) {
        this.quotaService = quotaService;
        this.copyCost = copyCost;
    }

    /** 首次出稿扣费；幂等键冲突（重复提交）时幂等返回，不重复扣费 */
    public void chargeForGenerate(AuthPrincipal principal, Long sessionId) {
        charge(principal, sessionId, "chat:" + sessionId + ":gen", "对话出稿扣费");
    }

    /** 微调扣费：revise 次数递增保证幂等键唯一 */
    public void chargeForRevise(AuthPrincipal principal, Long sessionId, int reviseSeq) {
        charge(principal, sessionId, "chat:" + sessionId + ":rev:" + reviseSeq, "对话微调扣费");
    }

    private void charge(AuthPrincipal principal, Long sessionId, String idempotentKey, String remark) {
        Long accountId = quotaService.resolveChargeAccountId(principal.getTenantId(),
                principal.getOrgId(), principal.getUserId(), principal.getRole());
        quotaService.chargeAccount(principal.getTenantId(), accountId, copyCost, idempotentKey, remark);
    }
}
