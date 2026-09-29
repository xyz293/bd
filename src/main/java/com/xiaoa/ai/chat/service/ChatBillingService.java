package com.xiaoa.ai.chat.service;

import com.xiaoa.common.auth.AuthPrincipal;
import com.xiaoa.quota.service.QuotaService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 对话计费（方案 ④ verify_and_charge 的额度预扣三态）：
 * 预扣(hold) → 成功(确认) → 失败/放弃(释放)，防止「扣了不出货」。
 *
 * <ul>
 *   <li>hold：幂等键 chat:{sessionId}:hold:{seq}（seq 为会话级 Redis 自增），预扣即扣费落流水</li>
 *   <li>confirm：文案出稿成功后调用（资金已在 hold 时扣除，仅语义确认）</li>
 *   <li>release：生成失败/挂起放弃时按 hold 键幂等退回（release:{holdKey}）</li>
 * </ul>
 *
 * <p>微调（revise）保持原有递增幂等键扣费；图/视频由 AI 网关内部扣费（gen:{workId}），
 * 均不走本类三态。</p>
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

    /** 文案出稿单价，供额度预校验 */
    public long getCopyCost() { return copyCost; }

    /**
     * 额度预扣（hold）：余额不足抛 3001，扣费失败随调用方事务回滚。
     *
     * @param holdKey 幂等键 chat:{sessionId}:hold:{seq}
     */
    public void checkAndHold(AuthPrincipal principal, String holdKey) {
        quotaService.chargeAccount(principal.getTenantId(), resolveAccountId(principal),
                copyCost, holdKey, "对话创作预扣");
    }

    /** 确认（confirm）：预扣即扣费，成功后无需资金操作，保留扩展点（如转正式流水备注）。 */
    public void confirmHold() {
        // 预扣即扣费，确认无需资金操作
    }

    /** 释放（release）：失败/放弃时按 hold 幂等键退回。 */
    public void releaseHold(AuthPrincipal principal, String holdKey) {
        quotaService.releaseHold(principal.getTenantId(), principal.getOrgId(),
                principal.getUserId(), principal.getRole(), copyCost, holdKey);
    }

    /** 微调扣费：revise 次数递增保证幂等键唯一 */
    public void chargeForRevise(AuthPrincipal principal, Long sessionId, int reviseSeq) {
        quotaService.chargeAccount(principal.getTenantId(), resolveAccountId(principal),
                copyCost, "chat:" + sessionId + ":rev:" + reviseSeq, "对话微调扣费");
    }

    private Long resolveAccountId(AuthPrincipal principal) {
        return quotaService.resolveChargeAccountId(principal.getTenantId(), principal.getOrgId(),
                principal.getUserId(), principal.getRole());
    }
}
