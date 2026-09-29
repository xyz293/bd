package com.xiaoa.ai.chat.agent;

import com.xiaoa.ai.chat.graph.ChatFlowState;
import com.xiaoa.ai.chat.provider.LlmProvider;
import com.xiaoa.ai.chat.provider.LlmRequest;
import com.xiaoa.ai.chat.provider.LlmResponse;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * ② 意图理解 Agent（understandIntent 节点）：
 * 一次 LLM 调用完成意图三分类（NEW_CREATE/REVISE/CONSULT）+ 任务类型 + 槽位抽取；
 * 缺口与增益候选由代码按预注册槽位字典计算（LLM 不得发明槽位 key）。
 * LLM 失败时兜底为新创作 + 文案，保证链路可用。
 *
 * <p>隔离记忆：intent / taskType / 本轮抽取到的缺口与增益候选。</p>
 */
@Component
public class IntentAgent extends BaseNodeAgent {

    /** 必填槽位（方案 ②：platform 等可 AI 代选，仅商品为「不给没法做」） */
    private static final List<String> REQUIRED_SLOTS = Collections.singletonList("product");

    private final LlmProvider llmProvider;

    public IntentAgent(AgentMemoryService agentMemory, LlmProvider llmProvider) {
        super(agentMemory);
        this.llmProvider = llmProvider;
    }

    @Override
    public String name() {
        return "intentAgent";
    }

    @Override
    public String systemPrompt() {
        return "意图理解 Agent：一次 LLM 调用完成意图三分类（NEW_CREATE/REVISE/CONSULT）、任务类型（COPY/IMAGE/VIDEO）"
                + "与槽位抽取；缺口与增益候选由代码按预注册槽位字典计算，LLM 不得发明槽位 key。";
    }

    @Override
    public void invoke(ChatFlowState state) {
        try {
            LlmResponse response = llmProvider.complete(LlmRequest.intent(state.getSession().getScene(),
                    state.getContextJson(), state.getHistoryJson(), state.getUserInput()));
            state.setIntent(normalizeIntent(response.getIntent()));
            state.setTaskType(normalizeTaskType(response.getTaskType()));
            state.setContextJson(mergeContext(state.getContextJson(), response.getContextPatchJson()));
        } catch (RuntimeException exception) {
            state.setIntent(ChatFlowState.INTENT_NEW_CREATE);
            state.setTaskType(ChatFlowState.TASK_COPY);
        }
        state.setMissingRequired(computeMissing(state.getContextJson()));
        state.setEnhancementCandidates(computeEnhancements(state));
        // 隔离记忆：本 Agent 只记自己职责内的判定结论
        remember(state, "intent", state.getIntent());
        remember(state, "taskType", state.getTaskType());
        remember(state, "missingRequired", jsonList(state.getMissingRequired()));
        remember(state, "enhancementCandidates", jsonList(state.getEnhancementCandidates()));
    }

    private String normalizeIntent(String intent) {
        if (ChatFlowState.INTENT_CONSULT.equals(intent) || ChatFlowState.INTENT_REVISE.equals(intent)) {
            return intent;
        }
        return ChatFlowState.INTENT_NEW_CREATE;
    }

    private String normalizeTaskType(String taskType) {
        if (ChatFlowState.TASK_IMAGE.equals(taskType) || ChatFlowState.TASK_VIDEO.equals(taskType)) {
            return taskType;
        }
        return ChatFlowState.TASK_COPY;
    }

    /** 缺口计算（代码管不失控）：预注册必填项逐一比对槽位快照。 */
    private List<String> computeMissing(String contextJson) {
        com.fasterxml.jackson.databind.JsonNode context = parse(contextJson);
        List<String> missing = new ArrayList<String>();
        for (String required : REQUIRED_SLOTS) {
            if (isBlank(textOf(context, required))) {
                missing.add(required);
            }
        }
        return missing;
    }

    /** 增益候选：日历有近节点且未定 festival / tone 未定 / versions 未定。 */
    private List<String> computeEnhancements(ChatFlowState state) {
        com.fasterxml.jackson.databind.JsonNode context = parse(state.getContextJson());
        List<String> candidates = new ArrayList<String>();
        if (!state.getCalendarFestivals().isEmpty() && isBlank(textOf(context, "festival"))) {
            candidates.add("festival");
        }
        if (isBlank(textOf(context, "tone"))) {
            candidates.add("tone");
        }
        if (isBlank(textOf(context, "versions"))) {
            candidates.add("versions");
        }
        return candidates;
    }
}
