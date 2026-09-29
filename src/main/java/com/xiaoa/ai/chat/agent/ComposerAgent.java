package com.xiaoa.ai.chat.agent;

import com.xiaoa.ai.chat.graph.ChatFlowState;
import com.xiaoa.ai.chat.model.ChatMessage;
import com.xiaoa.ai.chat.provider.LlmProvider;
import com.xiaoa.ai.chat.provider.LlmRequest;
import com.xiaoa.ai.chat.provider.LlmResponse;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 回复组装 Agent（responseComposer 节点）：
 * 三段式回复（✅已确认事实 / 🎨创意表达 / ⚠️待核实）+ 出稿引导语包装（LLM 失败模板兜底），
 * 并生成 AI 消息 JSON（动作 + 话术 + 版本 + 选项卡/媒体挂起载荷 + 微调来源）。
 *
 * <p>隔离记忆：本轮最终回复动作。</p>
 */
@Component
public class ComposerAgent extends BaseNodeAgent {

    private final LlmProvider llmProvider;

    public ComposerAgent(AgentMemoryService agentMemory, LlmProvider llmProvider) {
        super(agentMemory);
        this.llmProvider = llmProvider;
    }

    @Override
    public String name() {
        return "composerAgent";
    }

    @Override
    public String systemPrompt() {
        return "回复组装 Agent：把本轮结果组装成三段式回复（事实/创意/待核实）与 AI 消息 JSON；只做包装不产生新决策。";
    }

    @Override
    public void invoke(ChatFlowState state) {
        if (ChatFlowState.REPLY_GENERATE.equals(state.getReplyAction())) {
            state.setQuestion(composeThreeStage(state));
        }
        if (isBlank(state.getQuestion())) {
            state.setQuestion(FALLBACK_QUESTION);
        }
        state.setAiMessage(message(state, ChatMessage.ROLE_AI, aiJson(state)));
        remember(state, "replyAction", String.valueOf(state.getReplyAction()));
    }

    /** 三段式回复：事实/创意/待核实分层，出稿引导语 LLM 包装失败时模板兜底。 */
    private String composeThreeStage(ChatFlowState state) {
        StringBuilder question = new StringBuilder();
        com.xiaoa.ai.chat.graph.FactReport report = state.getFactReport();
        if (report != null && (!report.getConfirmedFacts().isEmpty() || !report.getTodoVerifyFacts().isEmpty())) {
            question.append("✅ 已确认事实：").append(String.join("、", report.getConfirmedFacts())).append("\n");
            question.append("🎨 创意表达：以下内容由 AI 创作生成\n");
            question.append("⚠️ 待你核实：").append(report.getTodoVerifyFacts().isEmpty() ? "无"
                    : String.join("、", report.getTodoVerifyFacts())).append("\n\n");
        }
        try {
            LlmResponse response = llmProvider.complete(LlmRequest.compose(
                    state.getSession().getScene(), state.getContextJson(), state.getHistoryJson(),
                    state.getUserInput()));
            if (!isBlank(response.getQuestion())) {
                question.append(response.getQuestion().trim());
                return question.toString();
            }
        } catch (RuntimeException ignored) {
            // 组装失败用模板兜底
        }
        com.fasterxml.jackson.databind.JsonNode context = parse(state.getContextJson());
        String product = textOf(context, "product");
        int count = state.getVersions() == null ? 0 : state.getVersions().size();
        question.append(isBlank(product) ? "" : "已按「" + product.trim() + "」")
                .append("出好 ").append(count).append(" 版内容，点任意一版可直接微调～");
        return question.toString();
    }

    /** AI 消息 JSON：动作 + 话术 + 版本 + 选项卡/媒体挂起载荷 + 微调来源。 */
    private String aiJson(ChatFlowState state) {
        try {
            Map<String, Object> payload = new LinkedHashMap<String, Object>();
            payload.put("action", state.getReplyAction());
            if (state.getQuestion() != null) {
                payload.put("question", state.getQuestion());
            }
            if (state.getVersions() != null) {
                payload.put("versions", state.getVersions());
            }
            if (state.getOptionCard() != null) {
                payload.put("optionCard", state.getOptionCard());
            }
            if (state.getMediaWorkId() != null) {
                payload.put("workId", state.getMediaWorkId());
            }
            if (state.getRevisedFrom() != null) {
                payload.put("revisedFrom", state.getRevisedFrom());
            }
            if (!state.getAiDecidedSlots().isEmpty()) {
                payload.put("aiDecidedSlots", state.getAiDecidedSlots());
            }
            return objectMapper.writeValueAsString(payload);
        } catch (Exception exception) {
            throw new IllegalStateException("无法序列化 AI 消息", exception);
        }
    }
}
