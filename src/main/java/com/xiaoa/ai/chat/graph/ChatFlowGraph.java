package com.xiaoa.ai.chat.graph;

import com.xiaoa.ai.chat.agent.ComposerAgent;
import com.xiaoa.ai.chat.agent.GenerateAgent;
import com.xiaoa.ai.chat.agent.GateAgent;
import com.xiaoa.ai.chat.agent.OptionAgent;
import com.xiaoa.ai.chat.agent.SkillAgent;
import com.xiaoa.ai.graph.CompiledGraph;
import com.xiaoa.ai.graph.NodeListener;
import com.xiaoa.ai.graph.StateGraph;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 珠宝门店 AI 创作顾问 · 对话创作状态图（极简 5-Agent 编排）。
 * <p>每个图节点 = 一个 Agent（{@link com.xiaoa.ai.chat.agent} 包），
 * Agent 记忆按命名空间隔离（AgentMemoryService：chat:agent:{threadId}:{agentName}），
 * 每个 Agent 只持有自己业务逻辑所需的记忆，跨 Agent 通信走共享 {@link ChatFlowState}。</p>
 *
 * <pre>
 * START ‹resume›
 *   ├─ REVISE(mode，REST 微调) ──────────────> generateContent（按最近出稿改写）──┐
 *   ├─ OPTION(选项卡恢复) ──> applyOption ──> gateAssess ‹sufficiency/intent›    │
 *   ├─ TIMEOUT(超时兜底) ───────────────────> gateAssess                        │
 *   └─ NEW(新消息) ────────────────────────> gateAssess                         │
 *        ├─ CONSULT（闲聊/咨询）──────────────────────────────> compose ──> END   │
 *        ├─ REVISE（对话内微调意图）──────────────────────────> generateContent ──┤
 *        ├─ NOT_ENOUGH ──> presentOptionCard（挂起，30s 倒计时/60s 兜底）──> compose │
 *        └─ ENOUGH ──> skillInvoke（ReAct 收集知识）──> generateContent（预扣额度+生成+合规）┘
 * </pre>
 *
 * <p>五个 Agent 职责：</p>
 * <ul>
 *   <li>GateAgent：判断信息够不够（兼意图识别/槽位抽取/营销日历装配）；</li>
 *   <li>SkillAgent：收集知识（ReAct：思考→行动→观察，只信技能返回）；</li>
 *   <li>OptionAgent：给出 option（维度化选项卡挂起/应用选择回 Gate 复判）；</li>
 *   <li>GenerateAgent：生成（兼额度预扣、微调改写、合规过滤）；</li>
 *   <li>ComposerAgent：整合（咨询/额度话术/三段式包装 + AI 消息落库 + checkpoint + 回复 VO）。</li>
 * </ul>
 *
 * <p>事务语义：事务边界在 {@code ChatFlowService}（@Transactional）。预扣（hold）在同一事务内，
 * 任何异常整体回滚即自动释放额度。</p>
 */
@Component
public class ChatFlowGraph {

    private final GateAgent gateAgent;
    private final SkillAgent skillAgent;
    private final OptionAgent optionAgent;
    private final GenerateAgent generateAgent;
    private final ComposerAgent composerAgent;

    private final CompiledGraph<ChatFlowState> graph;

    public ChatFlowGraph(GateAgent gateAgent, SkillAgent skillAgent, OptionAgent optionAgent,
                         GenerateAgent generateAgent, ComposerAgent composerAgent) {
        this.gateAgent = gateAgent;
        this.skillAgent = skillAgent;
        this.optionAgent = optionAgent;
        this.generateAgent = generateAgent;
        this.composerAgent = composerAgent;
        this.graph = buildGraph();
    }

    /** 执行整张对话流程图，返回最终状态（reply 挂在 state 上）。 */
    public @NonNull ChatFlowState run(@NonNull ChatFlowState state) {
        return graph.invoke(state);
    }

    /** 执行整张图并回调节点监听器（对齐 LangGraph stream 模式，供 WebSocket 阶段进度推送）。 */
    public @NonNull ChatFlowState run(@NonNull ChatFlowState state, NodeListener<ChatFlowState> listener) {
        return graph.invoke(state, listener);
    }

    private CompiledGraph<ChatFlowState> buildGraph() {
        return new StateGraph<ChatFlowState>()
                // 每个节点 = 一个 Agent（OptionAgent 占两个节点：出卡挂起 / 应用选择）
                .addNode("gateAssess", gateAgent::invoke)
                .addNode("skillInvoke", skillAgent::invoke)
                .addNode("generateContent", generateAgent::invoke)
                .addNode("presentOptionCard", optionAgent::present)
                .addNode("applyOption", optionAgent::applyChoice)
                .addNode("compose", composerAgent::invoke)
                // START 路由：正常新消息 / 选项卡恢复 / 超时兜底 / REST 微调
                .addConditionalEdges(StateGraph.START, this::resumeRouter,
                        branches(ChatFlowState.RESUME_NEW, "gateAssess",
                                ChatFlowState.RESUME_OPTION, "applyOption",
                                ChatFlowState.RESUME_TIMEOUT, "gateAssess",
                                ChatFlowState.INTENT_REVISE, "generateContent"))
                // Gate：一次 LLM 完成意图 + 抽槽位 + 充分性判断
                .addConditionalEdges("gateAssess", this::routeAfterGate,
                        branches(ChatFlowState.INTENT_CONSULT, "compose",
                                ChatFlowState.INTENT_REVISE, "generateContent",
                                "enough", "skillInvoke",
                                "short", "presentOptionCard"))
                // 核心循环：用户选择选项卡后再次进入 Gate 判断（3 轮封顶 AI 代选）
                .addEdge("applyOption", "gateAssess")
                // 挂起也走统一出口（选项卡消息落库，前端展示倒计时卡片）
                .addEdge("presentOptionCard", "compose")
                // 够 → 收集知识 → 生成 → 整合落库
                .addEdge("skillInvoke", "generateContent")
                .addEdge("generateContent", "compose")
                .addEdge("compose", StateGraph.END)
                .compile();
    }

    // ==================== 路由（图级决策，逻辑在 Agent） ====================

    /** REST 微调请求优先；否则按恢复类型路由（选项卡选择 / 超时兜底 / 正常新消息）。 */
    private String resumeRouter(ChatFlowState state) {
        if (state.getMode() == ChatFlowState.Mode.REVISE) {
            return ChatFlowState.INTENT_REVISE;
        }
        String resumeType = state.getResumeType();
        if (ChatFlowState.RESUME_OPTION.equals(resumeType) || ChatFlowState.RESUME_TIMEOUT.equals(resumeType)) {
            return resumeType;
        }
        return ChatFlowState.RESUME_NEW;
    }

    /** Gate 结果路由：咨询/微调意图不进创作循环；新创作按充分性分流（挂起 or 收集知识）。 */
    private String routeAfterGate(ChatFlowState state) {
        if (ChatFlowState.INTENT_CONSULT.equals(state.getIntent())) {
            return ChatFlowState.INTENT_CONSULT;
        }
        if (ChatFlowState.INTENT_REVISE.equals(state.getIntent())) {
            return ChatFlowState.INTENT_REVISE;
        }
        return state.isSufficient() ? "enough" : "short";
    }

    private @NonNull Map<String, String> branches(@NonNull String... keyValues) {
        Map<String, String> mapping = new HashMap<String, String>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            mapping.put(keyValues[i], keyValues[i + 1]);
        }
        return mapping;
    }
}
