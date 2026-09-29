package com.xiaoa.ai.chat.agent;

import com.xiaoa.ai.chat.graph.ChatFlowState;
import com.xiaoa.ai.chat.service.ChatMemoryService;
import org.springframework.stereotype.Component;

/**
 * 问卷答案合并 Agent（mergeAnswers 节点，/chat/answer 兼容入口）：
 * 旧版问卷作答恢复时合并答案进槽位；合并后统一交 Gate 复判（而非旧版回意图节点）。
 *
 * <p>隔离记忆：记录本轮合并的槽位 key。</p>
 */
@Component
public class AnswerAgent extends BaseNodeAgent {

    private final ChatMemoryService memoryService;
    private final ContextLoader contextLoader;

    public AnswerAgent(AgentMemoryService agentMemory, ChatMemoryService memoryService,
                       ContextLoader contextLoader) {
        super(agentMemory);
        this.memoryService = memoryService;
        this.contextLoader = contextLoader;
    }

    @Override
    public String name() {
        return "answerAgent";
    }

    @Override
    public String systemPrompt() {
        return "答案合并 Agent：问卷作答恢复时把答案合并进槽位快照并清挂起，交回 Gate 复判。";
    }

    @Override
    public void invoke(ChatFlowState state) {
        contextLoader.load(state);
        if (!state.getAnswers().isEmpty()) {
            try {
                String patch = objectMapper.writeValueAsString(
                        new java.util.LinkedHashMap<String, String>(state.getAnswers()));
                state.setContextJson(mergeContext(state.getContextJson(), patch));
            } catch (Exception ignored) {
                // 合并失败保留原槽位
            }
            memoryService.track(state.tenantId(), "questionnaire_answer");
            remember(state, "mergedSlots", jsonList(new java.util.ArrayList<String>(state.getAnswers().keySet())));
        }
        int round = memoryService.loadQuestionnaireRound(state.getSession().getId()) + 1;
        state.setQuestionnaireRound(round);
        memoryService.saveQuestionnaireRound(state.getSession().getId(), round);
        memoryService.clearPending(state.getSession().getId());
    }
}
