package com.xiaoa.ai.chat.graph;

import com.xiaoa.ai.chat.agent.ChargeAgent;
import com.xiaoa.ai.chat.agent.ComposerAgent;
import com.xiaoa.ai.chat.agent.ConsultAgent;
import com.xiaoa.ai.chat.agent.ContextAgent;
import com.xiaoa.ai.chat.agent.GenerateAgent;
import com.xiaoa.ai.chat.agent.GateAgent;
import com.xiaoa.ai.chat.agent.IntentAgent;
import com.xiaoa.ai.chat.agent.MediaAgent;
import com.xiaoa.ai.chat.agent.OptionAgent;
import com.xiaoa.ai.chat.agent.PersistAgent;
import com.xiaoa.ai.chat.agent.QuotaAgent;
import com.xiaoa.ai.chat.agent.ReviewAgent;
import com.xiaoa.ai.chat.agent.ReviseAgent;
import com.xiaoa.ai.chat.agent.SkillAgent;
import com.xiaoa.ai.chat.agent.AnswerAgent;
import com.xiaoa.ai.graph.CompiledGraph;
import com.xiaoa.ai.graph.NodeListener;
import com.xiaoa.ai.graph.StateGraph;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 珠宝门店 AI 创作顾问 · 对话创作状态图（Agent 化编排）。
 * 每个图节点 = 一个 Agent（{@link com.xiaoa.ai.chat.agent} 包），
 * Agent 记忆按命名空间隔离（AgentMemoryService：chat:agent:{threadId}:{agentName}），
 * 每个 Agent 只持有自己业务逻辑所需的记忆，跨 Agent 通信走共享 {@link ChatFlowState}。
 *
 * <p>核心循环（HITL Gate，额度逻辑保持不变）：</p>
 * <pre>
 * START ‹resume›──NEW──> contextAgent ──> intentAgent ‹intent›
 *   │                                      ├─ CONSULT ──> consultAgent ──────────────────────────┐
 *   │                                      ├─ REVISE ───> reviseValidate → reviseCallLlm         │
 *   │                                      │              → reviseCompliance → reviseBilling ────┤
 *   │                                      └─ NEW_CREATE ─> gateAgent ‹sufficiency›               │
 *   │                                              ├─ ENOUGH ──> skillAgent → verifyAndCharge ‹quota›
 *   │                                              │                              ├─ ok ──> generateAgent ‹taskType›
 *   │                                              │                              │           ├─ COPY ──> reviewAgent → billingConfirm ┐
 *   │                                              │                              │           └─ MEDIA ─> mediaAgent(挂起) ────────────┤
 *   │                                              │                              └─ short ─> quotaAgent ─────────────────────────────┤
 *   │                                              └─ NOT_ENOUGH ─> presentOptionCard(挂起) ─────────────────────────────────────────────────┤
 *   │                                                                                                                                       v
 *   └──ANSWER──> mergeAnswers ──┐                                           responseComposer ─> persist ─> END
 *      └──OPTION──> applyOption ┴──> gateAgent（用户选择后再次判断，循环；3 轮封顶 AI 代选）
 *      └──TIMEOUT─> gateAgent（AI 代选补齐 → 技能 → 生成）
 * </pre>
 *
 * <p>事务语义：事务边界在 {@code ChatFlowService}（@Transactional）。预扣（hold）在同一事务内，
 * 任何异常整体回滚即自动释放额度；confirm 为资金无操作（预扣即扣费）。</p>
 */
@Component
public class ChatFlowGraph {

    private final ContextAgent contextAgent;
    private final IntentAgent intentAgent;
    private final GateAgent gateAgent;
    private final OptionAgent optionAgent;
    private final SkillAgent skillAgent;
    private final ChargeAgent chargeAgent;
    private final GenerateAgent generateAgent;
    private final MediaAgent mediaAgent;
    private final ReviewAgent reviewAgent;
    private final ConsultAgent consultAgent;
    private final QuotaAgent quotaAgent;
    private final ReviseAgent reviseAgent;
    private final AnswerAgent answerAgent;
    private final ComposerAgent composerAgent;
    private final PersistAgent persistAgent;

    private final CompiledGraph<ChatFlowState> graph;

    public ChatFlowGraph(ContextAgent contextAgent, IntentAgent intentAgent, GateAgent gateAgent,
                         OptionAgent optionAgent, SkillAgent skillAgent, ChargeAgent chargeAgent,
                         GenerateAgent generateAgent, MediaAgent mediaAgent, ReviewAgent reviewAgent,
                         ConsultAgent consultAgent, QuotaAgent quotaAgent, ReviseAgent reviseAgent,
                         AnswerAgent answerAgent, ComposerAgent composerAgent, PersistAgent persistAgent) {
        this.contextAgent = contextAgent;
        this.intentAgent = intentAgent;
        this.gateAgent = gateAgent;
        this.optionAgent = optionAgent;
        this.skillAgent = skillAgent;
        this.chargeAgent = chargeAgent;
        this.generateAgent = generateAgent;
        this.mediaAgent = mediaAgent;
        this.reviewAgent = reviewAgent;
        this.consultAgent = consultAgent;
        this.quotaAgent = quotaAgent;
        this.reviseAgent = reviseAgent;
        this.answerAgent = answerAgent;
        this.composerAgent = composerAgent;
        this.persistAgent = persistAgent;
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
                // 每个节点 = 一个 Agent
                .addNode("fetchContext", contextAgent::invoke)
                .addNode("understandIntent", intentAgent::invoke)
                .addNode("gateAssess", gateAgent::invoke)
                .addNode("skillInvoke", skillAgent::invoke)
                .addNode("verifyAndCharge", chargeAgent::invoke)
                .addNode("presentOptionCard", optionAgent::present)
                .addNode("applyOption", optionAgent::applyChoice)
                .addNode("generateContent", generateAgent::invoke)
                .addNode("mediaSubmit", mediaAgent::invoke)
                .addNode("reviewAndRespond", reviewAgent::invoke)
                .addNode("billingConfirm", chargeAgent::confirmHoldNode)
                .addNode("respondConsult", consultAgent::invoke)
                .addNode("respondQuota", quotaAgent::invoke)
                .addNode("mergeAnswers", answerAgent::invoke)
                .addNode("reviseValidate", reviseAgent::validate)
                .addNode("reviseCallLlm", reviseAgent::callLlm)
                .addNode("reviseCompliance", reviseAgent::compliance)
                .addNode("reviseBilling", reviseAgent::billing)
                .addNode("responseComposer", composerAgent::invoke)
                .addNode("persist", persistAgent::invoke)
                // START 路由：正常新消息 / 挂起恢复（问卷作答兼容、选项卡选择、超时兜底）、微调
                .addConditionalEdges(StateGraph.START, this::resumeRouter,
                        branches(ChatFlowState.RESUME_NEW, "fetchContext",
                                ChatFlowState.RESUME_ANSWER, "mergeAnswers",
                                ChatFlowState.RESUME_OPTION, "applyOption",
                                ChatFlowState.RESUME_TIMEOUT, "gateAssess",
                                ChatFlowState.INTENT_REVISE, "reviseValidate"))
                .addEdge("fetchContext", "understandIntent")
                .addConditionalEdges("understandIntent", this::routeAfterIntent,
                        branches(ChatFlowState.INTENT_CONSULT, "respondConsult",
                                ChatFlowState.INTENT_REVISE, "reviseValidate",
                                ChatFlowState.INTENT_NEW_CREATE, "gateAssess"))
                // Gate 判定：够 → 技能取数；不够 → 选项卡挂起
                .addConditionalEdges("gateAssess",
                        state -> state.isSufficient() ? "enough" : "short",
                        branches("enough", "skillInvoke", "short", "presentOptionCard"))
                .addEdge("presentOptionCard", "responseComposer")
                // 兼容：问卷作答合并后也回 Gate 复判
                .addEdge("mergeAnswers", "gateAssess")
                // 核心循环：用户选择选项卡后再次进入 Gate 判断
                .addEdge("applyOption", "gateAssess")
                .addEdge("skillInvoke", "verifyAndCharge")
                .addConditionalEdges("verifyAndCharge",
                        state -> state.isQuotaOk() ? "ok" : "short",
                        branches("ok", "generateContent", "short", "respondQuota"))
                .addConditionalEdges("generateContent",
                        state -> ChatFlowState.TASK_COPY.equals(state.getTaskType()) ? "copy" : "media",
                        branches("copy", "reviewAndRespond", "media", "mediaSubmit"))
                .addEdge("mediaSubmit", "responseComposer")
                .addEdge("reviewAndRespond", "billingConfirm")
                .addEdge("billingConfirm", "responseComposer")
                .addEdge("respondConsult", "responseComposer")
                .addEdge("respondQuota", "responseComposer")
                .addEdge("reviseValidate", "reviseCallLlm")
                .addEdge("reviseCallLlm", "reviseCompliance")
                .addEdge("reviseCompliance", "reviseBilling")
                .addEdge("reviseBilling", "responseComposer")
                .addEdge("responseComposer", "persist")
                .addEdge("persist", StateGraph.END)
                .compile();
    }

    // ==================== 路由（图级决策，逻辑在 Agent） ====================

    /** 微调请求优先；否则按恢复类型路由（问卷作答 / 选项卡选择 / 超时兜底 / 正常新消息）。 */
    private String resumeRouter(ChatFlowState state) {
        if (state.getMode() == ChatFlowState.Mode.REVISE) {
            return ChatFlowState.INTENT_REVISE;
        }
        String resumeType = state.getResumeType();
        if (ChatFlowState.RESUME_ANSWER.equals(resumeType) || ChatFlowState.RESUME_OPTION.equals(resumeType)
                || ChatFlowState.RESUME_TIMEOUT.equals(resumeType)) {
            return resumeType;
        }
        return ChatFlowState.RESUME_NEW;
    }

    /** 意图路由：咨询直接回复；微调走改写链；新创作进入 Gate 充分性判断。 */
    private String routeAfterIntent(ChatFlowState state) {
        String intent = state.getIntent();
        if (ChatFlowState.INTENT_CONSULT.equals(intent)) {
            return ChatFlowState.INTENT_CONSULT;
        }
        if (ChatFlowState.INTENT_REVISE.equals(intent)) {
            return ChatFlowState.INTENT_REVISE;
        }
        return ChatFlowState.INTENT_NEW_CREATE;
    }

    private @NonNull Map<String, String> branches(@NonNull String... keyValues) {
        Map<String, String> mapping = new HashMap<String, String>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            mapping.put(keyValues[i], keyValues[i + 1]);
        }
        return mapping;
    }
}
