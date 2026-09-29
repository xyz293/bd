package com.xiaoa.ai.chat.agent;

import com.xiaoa.ai.chat.graph.ChatFlowState;
import org.springframework.stereotype.Component;

/**
 * 额度不足回复 Agent（respondQuota 节点）：
 * 预扣失败（额度不足）时终止本流程，提示充值（企业版引导找店长）。不产生扣费。
 *
 * <p>隔离记忆：记录本轮拦截所需额度。</p>
 */
@Component
public class QuotaAgent extends BaseNodeAgent {

    public QuotaAgent(AgentMemoryService agentMemory) {
        super(agentMemory);
    }

    @Override
    public String name() {
        return "quotaAgent";
    }

    @Override
    public String systemPrompt() {
        return "额度不足回复 Agent：预扣失败时终止流程提示充值，不扣费、不生成。";
    }

    @Override
    public void invoke(ChatFlowState state) {
        state.setReplyAction(ChatFlowState.REPLY_ASK);
        state.setQuestion("当前额度不足（本次创作约需 " + state.getQuotaNeed()
                + " 点），请联系店长或管理员充值后再试～");
        remember(state, "quotaNeed", String.valueOf(state.getQuotaNeed()));
    }
}
