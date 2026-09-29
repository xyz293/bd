package com.xiaoa.ai.chat.agent;

import com.xiaoa.ai.chat.graph.ChatFlowState;
import com.xiaoa.ai.chat.provider.LlmProvider;
import com.xiaoa.ai.chat.provider.LlmRequest;
import com.xiaoa.ai.chat.provider.LlmResponse;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 内容生成 Agent（generateContent 节点）：
 * Gate 判定足够且额度预扣成功后，直接调用大模型按「上下文槽位 + 技能取回资料 + 事实占位符」出多版文案；
 * LLM 失败/空结果用模板兜底。媒体任务由条件边转交 mediaSubmit 节点（MediaAgent），本节点不重复提交。
 *
 * <p>隔离记忆：本轮生成版数。</p>
 */
@Component
public class GenerateAgent extends BaseNodeAgent {

    private final LlmProvider llmProvider;
    private final ContextLoader contextLoader;

    public GenerateAgent(AgentMemoryService agentMemory, LlmProvider llmProvider, ContextLoader contextLoader) {
        super(agentMemory);
        this.llmProvider = llmProvider;
        this.contextLoader = contextLoader;
    }

    @Override
    public String name() {
        return "generateAgent";
    }

    @Override
    public String systemPrompt() {
        return "内容生成 Agent：信息足够且额度预扣成功后，直接调用大模型按上下文槽位 + 技能取回资料（skillFacts）"
                + "出多版文案；事实核验要求占位符的字段不得编造；媒体任务转交 mediaSubmit 节点。";
    }

    @Override
    public void invoke(ChatFlowState state) {
        ensureContext(state);
        if (!ChatFlowState.TASK_COPY.equals(state.getTaskType())) {
            // 媒体任务由条件边路由到 mediaSubmit 节点（MediaAgent），这里只留观测轨迹
            remember(state, "routedTo", "mediaSubmit");
            return;
        }
        List<String> versions = null;
        try {
            String genContext = mergeContext(state.getContextJson(),
                    patch("facts", state.getFactReport().toPromptFragment()));
            if (state.getSkillFacts() != null) {
                // 技能取回的客观资料并入生成上下文
                genContext = mergeContext(genContext, patch("skillFacts", state.getSkillFacts()));
            }
            LlmResponse response = llmProvider.complete(LlmRequest.chat(state.getSession().getScene(),
                    genContext, state.getHistoryJson(), state.getUserInput()));
            versions = response.getVersions();
        } catch (RuntimeException exception) {
            versions = null;
        }
        if (versions == null || versions.isEmpty() || allBlank(versions)) {
            versions = fallbackVersions(state);
        }
        state.setVersions(versions);
        state.setReplyAction(ChatFlowState.REPLY_GENERATE);
        remember(state, "versionsCount", String.valueOf(versions.size()));
    }

    /** 恢复链路（TIMEOUT 直入）兜底：上下文缺失时装配一次。 */
    private void ensureContext(ChatFlowState state) {
        if (state.getContextJson() == null || state.getContextJson().trim().isEmpty()) {
            contextLoader.load(state);
        }
    }
}
