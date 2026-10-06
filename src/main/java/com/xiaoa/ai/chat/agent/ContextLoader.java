package com.xiaoa.ai.chat.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xiaoa.ai.chat.graph.ChatFlowState;
import org.springframework.stereotype.Component;

/**
 * 上下文装配（无上下文模式，供各 Agent 复用）：
 * 不读取对话历史与跨轮槽位记忆，仅确保 state 有初始上下文容器——
 * 容器内容完全由「本轮编排」填充：意图抽取的槽位 + 用户选项卡选择的 clarification。
 * 挂起恢复（checkpoint）带来的已有上下文不会被覆盖。
 *
 * <p>人工上下文提示词轨迹：员工每次人工更新上下文（选项卡选择/打字补充）都会以
 * {@code AgentMemoryService#appendPromptTrail} 持续拼接进 Redis 任务记忆；
 * 本类在装配时把最新轨迹并入 {@code context.contextTrail}，
 * 各 Agent 的 LLM 调用（Gate 复判/选项卡出题/生成）自动看到累积的人工更新。</p>
 */
@Component
public class ContextLoader {

    private final AgentMemoryService agentMemory;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ContextLoader(AgentMemoryService agentMemory) {
        this.agentMemory = agentMemory;
    }

    /** 仅当上下文为空时初始化为空对象（不覆盖 checkpoint 恢复的编排内状态）；随后并入最新人工轨迹。 */
    public void load(ChatFlowState state) {
        if (state.getContextJson() == null || state.getContextJson().trim().isEmpty()) {
            state.setContextJson("{}");
        }
        mergePromptTrail(state);
        // 无上下文模式：historyJson 保持 null，LLM 调用不携带历史对话
    }

    /** 把 Redis 里最新的人工上下文轨迹并入 context.contextTrail（总是刷新为最新，覆盖旧值）。 */
    private void mergePromptTrail(ChatFlowState state) {
        String trail = agentMemory.readPromptTrail(state.getThreadId());
        if (trail == null || trail.trim().isEmpty()) {
            return;
        }
        try {
            JsonNode context = objectMapper.readTree(state.getContextJson());
            ObjectNode merged = objectMapper.createObjectNode();
            if (context.isObject()) {
                merged.setAll((ObjectNode) context);
            }
            merged.put("contextTrail", trail.trim());
            state.setContextJson(objectMapper.writeValueAsString(merged));
        } catch (Exception ignored) {
            // 轨迹并入失败不阻塞装配，上下文保持原样
        }
    }
}
