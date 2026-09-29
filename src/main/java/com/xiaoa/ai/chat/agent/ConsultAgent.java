package com.xiaoa.ai.chat.agent;

import com.xiaoa.ai.chat.graph.ChatFlowState;
import com.xiaoa.ai.chat.provider.LlmProvider;
import com.xiaoa.ai.chat.provider.LlmRequest;
import com.xiaoa.ai.chat.provider.LlmResponse;
import org.springframework.stereotype.Component;

/**
 * 咨询回复 Agent（respondConsult 节点）：
 * 闲聊/咨询不进创作链路、不扣费，直接口语化回复；LLM 失败时交回退话术。
 *
 * <p>隔离记忆：无创作产物，仅记录本轮为咨询。</p>
 */
@Component
public class ConsultAgent extends BaseNodeAgent {

    private final LlmProvider llmProvider;

    public ConsultAgent(AgentMemoryService agentMemory, LlmProvider llmProvider) {
        super(agentMemory);
        this.llmProvider = llmProvider;
    }

    @Override
    public String name() {
        return "consultAgent";
    }

    @Override
    public String systemPrompt() {
        return "咨询回复 Agent：闲聊/咨询直接口语化回复，不进创作链路、不扣额度。";
    }

    @Override
    public void invoke(ChatFlowState state) {
        String text = state.getUserInput();
        try {
            LlmResponse response = llmProvider.complete(LlmRequest.compose(
                    state.getSession().getScene(), state.getContextJson(), state.getHistoryJson(), text));
            state.setQuestion(response.getQuestion());
        } catch (RuntimeException exception) {
            state.setQuestion(null);
        }
        state.setReplyAction(ChatFlowState.REPLY_ASK);
        remember(state, "mode", "consult");
    }
}
