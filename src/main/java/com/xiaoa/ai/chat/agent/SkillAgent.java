package com.xiaoa.ai.chat.agent;

import com.xiaoa.ai.chat.agent.skill.Skill;
import com.xiaoa.ai.chat.agent.skill.SkillRegistry;
import com.xiaoa.ai.chat.graph.ChatFlowState;
import com.xiaoa.ai.chat.provider.AgentPromptProperties;
import com.xiaoa.ai.chat.provider.LlmProvider;
import com.xiaoa.ai.chat.provider.LlmRequest;
import com.xiaoa.ai.chat.provider.LlmResponse;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 技能调用 Agent（skillInvoke 节点，ReAct 模式）：
 * 不再一次性执行 Gate 给出的技能清单，而是「思考 → 行动 → 观察」循环——
 * <pre>
 * Thought(分析还缺什么) → Action(调一个技能) → Observation(拿到资料) → Thought → ... → FINISH
 * </pre>
 * LLM 自主决定调用哪个技能、什么顺序、是否继续；约束：
 * <ul>
 *   <li>技能只能从 {@link SkillRegistry} 选（LLM 不得发明工具，未知技能回错误观察让其纠正）；</li>
 *   <li>轮数封顶（{@value #MAX_REACT_STEPS} 步）保证收敛，超限按已收集资料降级收尾；</li>
 *   <li>每轮 thought/action/observation 轨迹写入本 Agent 隔离记忆（reactTrace），只属于本节点业务。</li>
 * </ul>
 * 提示词为本 Agent 专属的 ReAct 契约（YAML xiaoa.ai.llm.prompts.react 可配，随请求携带）。
 * 产出仍为 state.skillFacts（[{skill,info}]），下游生成 Agent 拼装 prompt 用法不变。
 */
@Component
public class SkillAgent extends BaseNodeAgent {

    /** ReAct 步数上限（含 FINISH 前的行动步），保证收敛 */
    private static final int MAX_REACT_STEPS = 5;
    /** ReAct 结束动作 */
    private static final String FINISH = "FINISH";

    private final LlmProvider llmProvider;
    private final SkillRegistry skillRegistry;

    public SkillAgent(AgentMemoryService agentMemory, AgentPromptProperties prompts,
                      LlmProvider llmProvider, SkillRegistry skillRegistry) {
        super(agentMemory, prompts);
        this.llmProvider = llmProvider;
        this.skillRegistry = skillRegistry;
    }

    @Override
    public String name() {
        return "skillAgent";
    }

    @Override
    public String systemPrompt() {
        return prompts.getReact(); // SkillAgent 专属提示词（YAML xiaoa.ai.llm.prompts.react 可配）
    }

    @Override
    public void invoke(ChatFlowState state) {
        // ReAct 轨迹：首条任务描述 + (assistant 思考/行动, user 观察) 交替
        List<Map<String, String>> messages = new ArrayList<Map<String, String>>();
        messages.add(userMessage(initialTask(state)));
        List<Map<String, String>> facts = new ArrayList<Map<String, String>>();
        List<String> trace = new ArrayList<String>();

        for (int step = 1; step <= MAX_REACT_STEPS; step++) {
            LlmResponse stepOut;
            try {
                stepOut = llmProvider.complete(withPrompt(LlmRequest.react(
                        state.getSession().getScene(), state.getContextJson(), messages)));
            } catch (RuntimeException exception) {
                trace.add("step" + step + ": LLM 调用失败，按已收集资料降级收尾");
                break;
            }
            String thought = stepOut.getThought();
            String toolName = stepOut.getToolName() == null ? "" : stepOut.getToolName().trim();
            trace.add("step" + step + " thought: " + summarize(thought));

            if (FINISH.equalsIgnoreCase(toolName)) {
                trace.add("step" + step + " action: FINISH");
                break;
            }

            Skill skill = skillRegistry.find(toolName);
            String observation;
            if (skill == null) {
                // LLM 发明了不存在的工具：观察返回错误，让下一轮自行纠正
                observation = "错误：未知技能「" + toolName + "」。可用技能：" + skillRegistry.catalog()
                        + "，或 FINISH 结束。";
            } else {
                try {
                    observation = skill.invoke(state, parse(stepOut.getToolArgs()));
                    Map<String, String> fact = new LinkedHashMap<String, String>();
                    fact.put("skill", skill.name());
                    fact.put("info", observation);
                    facts.add(fact);
                } catch (RuntimeException exception) {
                    observation = "技能执行失败：" + skill.name() + "，可换其它技能或 FINISH 结束。";
                }
            }
            trace.add("step" + step + " action: " + toolName + " -> " + summarize(observation));
            messages.add(assistantMessage(thought, toolName));
            messages.add(observationMessage(observation));
        }

        // 产出：收集到的资料（下游生成 Agent 拼 prompt）+ 隔离记忆（完整 ReAct 轨迹）
        try {
            state.setSkillFacts(facts.isEmpty() ? null : objectMapper.writeValueAsString(facts));
        } catch (Exception exception) {
            state.setSkillFacts(null);
        }
        remember(state, "reactTrace", jsonList(trace));
        remember(state, "facts", String.valueOf(state.getSkillFacts()));
    }

    // ==================== 轨迹消息构造 ====================

    /** 首条任务：目标 + 当前上下文 + Gate 建议技能 + 可用技能目录。 */
    private String initialTask(ChatFlowState state) {
        try {
            Map<String, Object> task = new LinkedHashMap<String, Object>();
            task.put("task", "为「" + defaultIfBlank(state.getSession().getScene(), "朋友圈")
                    + "」内容创作收集必要资料");
            task.put("context", parse(state.getContextJson()));
            task.put("gateSuggestedSkills", state.getNeedSkills());
            task.put("catalog", skillRegistry.catalog());
            task.put("rule", "每次只输出一步(thought+action)；资料足够立即 FINISH；最多 " + MAX_REACT_STEPS + " 步");
            return objectMapper.writeValueAsString(task);
        } catch (Exception exception) {
            return "{\"task\":\"收集创作资料\"}";
        }
    }

    private Map<String, String> userMessage(String content) {
        Map<String, String> message = new LinkedHashMap<String, String>();
        message.put("role", "user");
        message.put("content", content);
        return message;
    }

    /** assistant 步骤消息：thought + action（与解析端契约一致）。 */
    private Map<String, String> assistantMessage(String thought, String action) {
        Map<String, String> message = new LinkedHashMap<String, String>();
        message.put("role", "assistant");
        try {
            Map<String, String> step = new LinkedHashMap<String, String>();
            step.put("thought", summarize(thought));
            step.put("action", action);
            message.put("content", objectMapper.writeValueAsString(step));
        } catch (Exception exception) {
            message.put("content", "{\"action\":\"" + action + "\"}");
        }
        return message;
    }

    /** user 观察消息：observation（技能返回的资料或错误提示）。 */
    private Map<String, String> observationMessage(String observation) {
        Map<String, String> message = new LinkedHashMap<String, String>();
        message.put("role", "user");
        try {
            Map<String, String> payload = new LinkedHashMap<String, String>();
            payload.put("observation", observation);
            message.put("content", objectMapper.writeValueAsString(payload));
        } catch (Exception exception) {
            message.put("content", String.valueOf(observation));
        }
        return message;
    }

    /** 观测/思考摘要（隔离记忆与轨迹里只留前 120 字符，防膨胀）。 */
    private String summarize(String text) {
        if (text == null) {
            return "";
        }
        String trimmed = text.replaceAll("\\s+", " ").trim();
        return trimmed.length() > 120 ? trimmed.substring(0, 120) + "…" : trimmed;
    }
}
