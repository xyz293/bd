package com.xiaoa.ai.chat.service;

import com.xiaoa.ai.chat.dto.ChatOptionRequest;
import com.xiaoa.ai.chat.dto.ChatReplyVO;
import com.xiaoa.ai.chat.dto.ChatReviseRequest;
import com.xiaoa.ai.chat.dto.ChatSendRequest;
import com.xiaoa.ai.chat.dto.PendingOption;
import com.xiaoa.ai.chat.graph.ChatFlowGraph;
import com.xiaoa.ai.chat.graph.ChatFlowState;
import com.xiaoa.ai.chat.mapper.ChatMessageMapper;
import com.xiaoa.ai.chat.model.ChatMessage;
import com.xiaoa.ai.chat.model.ChatSession;
import com.xiaoa.ai.graph.NodeListener;
import com.xiaoa.ai.chat.agent.AgentMemoryService;
import com.xiaoa.common.auth.AuthContext;
import com.xiaoa.common.auth.AuthPrincipal;
import com.xiaoa.common.context.TenantContext;
import com.xiaoa.common.exception.BusinessException;
import com.xiaoa.common.exception.ErrorCode;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 对话编排入口（方案 §9 接口契约的三个入口）：会话鉴权、落用户消息、事务边界。
 *
 * <ul>
 *   <li>{@link #chat}：用户消息入口（POST /chat/message / WS chat.send）</li>
 *   <li>{@link #option}：选项卡选择（挂起恢复，POST /chat/option / WS chat.option）</li>
 *   <li>{@link #releaseOptionTimeout}：选项卡超时兜底放行（调度线程调用，默认 D 你帮我定）</li>
 * </ul>
 *
 * <p>具体流程编排（Gate/选项卡/额度/生成/合规/计费/落库）由状态图 {@link ChatFlowGraph}
 * 驱动：输入装入 {@link ChatFlowState} 后执行整张图并取回 reply。预扣（hold）在事务内，
 * 任何失败异常穿透图执行整体回滚（额度自动释放，消息不落，员工可重发）。</p>
 *
 * <p>任务 id 语义：任务 id 与会话 id 分离——每次编排生成新任务 id（task-{sessionId}-{短随机}），
 * Agent 隔离记忆与 checkpoint 都挂任务 id 上；新任务开始前释放上一任务残留并绑定新 id，
 * 作品生成等终结态由 {@code ComposerAgent} 释放当次任务态，任务态不跨创作复用。
 * 选项卡挂起后，恢复入口按「会话 → 任务 id」映射定位当次任务的记忆续跑。</p>
 */
@Service
public class ChatFlowService {

    private static final String PENDING_OPTION_CARD = "OPTION_CARD";

    private final ChatSessionService sessionService;
    private final ChatMessageMapper messageMapper;
    private final ChatMemoryService memoryService;
    private final ChatCheckpointService checkpointService;
    private final AgentMemoryService agentMemory;
    private final ChatFlowGraph chatFlowGraph;

    public ChatFlowService(ChatSessionService sessionService, ChatMessageMapper messageMapper,
                           ChatMemoryService memoryService, ChatCheckpointService checkpointService,
                           AgentMemoryService agentMemory, ChatFlowGraph chatFlowGraph) {
        this.sessionService = sessionService;
        this.messageMapper = messageMapper;
        this.memoryService = memoryService;
        this.checkpointService = checkpointService;
        this.agentMemory = agentMemory;
        this.chatFlowGraph = chatFlowGraph;
    }

    // ==================== 用户消息入口 ====================

    @Transactional
    public ChatReplyVO chat(AuthPrincipal principal, Long sessionId, ChatSendRequest request) {
        return chat(principal, sessionId, request, null);
    }

    /** 带节点监听器的执行（对齐 LangGraph stream，供 WebSocket 阶段进度推送）。 */
    @Transactional
    public ChatReplyVO chat(AuthPrincipal principal, Long sessionId, ChatSendRequest request,
                            @Nullable NodeListener<ChatFlowState> listener) {
        ChatSession session = sessionService.requireUsable(principal, sessionId);
        releaseStaleTask(sessionId);

        // 1. 存用户消息（任何后续失败随事务回滚，员工可重发）
        ChatMessage userMessage = message(principal.getTenantId(), session, ChatMessage.ROLE_USER, request.getText());
        messageMapper.insert(userMessage);

        // 2. 执行对话状态图：①获取数据 → ②理解意图 → ③问卷(挂起) / ④核验预扣 → ⑤选项卡(挂起)
        //    → ⑥生成 → ⑦审查回复 → 落库
        ChatFlowState state = ChatFlowState.forChat(principal, session, request.getText());
        // 选项卡挂起中员工直接打字补充（不点卡）：继承挂起任务已确认槽位，同一创作意图上下文不丢
        inheritPendingTaskContext(sessionId, state);
memoryService.bindTask(sessionId, state.getThreadId()); // 新任务绑定：挂起后恢复按它定位
// 人工上下文提示词轨迹：本轮人工输入拼接进 Redis 任务记忆（ContextLoader 并入上下文后各 Agent 可见）
agentMemory.appendPromptTrail(state.getThreadId(), "员工输入：" + request.getText());
runGraph(state, listener);
        return state.getReply();
    }

    // ==================== 选项卡选择（挂起恢复） ====================

    /** 抢占挂起（与超时兜底任务竞争，被抢走说明已默认放行）→ 应用用户选择 → 回 Gate 复判。 */
    @Transactional
    public ChatReplyVO option(AuthPrincipal principal, Long sessionId, ChatOptionRequest request) {
        ChatSession session = sessionService.requireUsable(principal, sessionId);
        if (!memoryService.isPending(sessionId, PENDING_OPTION_CARD)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "当前没有待选择的选项卡");
        }
        PendingOption pending = memoryService.takePendingOption(sessionId);
        if (pending == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "选项卡已失效，内容可能已按默认生成");
        }
        ChatMessage userMessage = message(principal.getTenantId(), session, ChatMessage.ROLE_USER,
                "{\"type\":\"option_card\",\"key\":\"" + request.getKey() + "\"}");
        messageMapper.insert(userMessage);

        ChatFlowState state = restoreCheckpoint(memoryService.currentTask(sessionId), session);
        if (state != null) {
            // 从「停止之前的记忆」续跑：覆盖恢复指令与当前登录态
            state.setResumeType(ChatFlowState.RESUME_OPTION);
            state.setOptionKey(request.getKey());
            state.setPrincipal(principal);
        } else {
            state = ChatFlowState.forOption(principal, session, request.getKey());
        }
        chatFlowGraph.run(state);
        return state.getReply();
    }

    // ==================== 选项卡超时兜底（调度线程，默认 D 直接生成） ====================

    /**
     * 挂起 >60s 兜底放行（前端 30s 计时，后端 60s 兜底）：抢占挂起后以离线身份重建
     * principal 执行图（默认 D，直接生成），结果照常落会话，用户回来可见。
     * 调度线程无登录态/租户态，此处显式设置并最终清理。
     */
    @Transactional
    public void releaseOptionTimeout(PendingOption pending) {
        if (memoryService.takePendingOption(pending.getSessionId()) == null) {
            return; // 已被用户抢占处理，跳过
        }
        AuthPrincipal principal = new AuthPrincipal(pending.getUserId(), pending.getTenantId(),
                pending.getOrgId(), pending.getRole(), pending.getDataScope());
        AuthContext.set(principal);
        TenantContext.setTenantId(pending.getTenantId());
        try {
            ChatSession session = sessionService.requireUsable(principal, pending.getSessionId());
            ChatFlowState state = restoreCheckpoint(memoryService.currentTask(pending.getSessionId()), session);
            if (state != null) {
                // 从「停止之前的记忆」续跑：默认 D 直接生成，覆盖恢复指令与当前身份
                state.setResumeType(ChatFlowState.RESUME_TIMEOUT);
                state.setPrincipal(principal);
            } else {
                state = ChatFlowState.forTimeout(principal, session);
            }
            chatFlowGraph.run(state);
            memoryService.track(pending.getTenantId(), "option_card_timeout");
        } finally {
            AuthContext.clear();
            TenantContext.clear();
        }
    }

    /**
     * 按任务 id 取回该任务停止前的图状态（LangGraph checkpoint 语义）。
     * 会话与登录态以当前请求为准覆盖，避免使用过期快照。
     */
    private ChatFlowState restoreCheckpoint(String taskId, ChatSession session) {
        ChatFlowState checkpoint = checkpointService.load(taskId);
        if (checkpoint == null) {
            return null;
        }
        checkpoint.setSession(session);
        return checkpoint;
    }

    // ==================== 微调入口 ====================

    @Transactional
    public ChatReplyVO revise(AuthPrincipal principal, Long sessionId, ChatReviseRequest request) {
        ChatSession session = sessionService.requireUsable(principal, sessionId);
        releaseStaleTask(sessionId);

        // 执行微调状态图：校验版本 → LLM 改写 → 合规 → 计费 → 落库
        ChatFlowState state = ChatFlowState.forRevise(principal, session, request);
        memoryService.bindTask(sessionId, state.getThreadId());
        chatFlowGraph.run(state);
        return state.getReply();
    }

    /**
     * 选项卡挂起中员工直接打字补充（不点卡）场景：把挂起任务已确认的槽位
     * （如刚说的「文案」、刚点过的主题）继承进新任务的上下文，
     * 避免同一创作意图的轮间记忆脱节（否则会重复问已确认维度）。
     */
    private void inheritPendingTaskContext(Long sessionId, ChatFlowState state) {
        if (!memoryService.isPending(sessionId, PENDING_OPTION_CARD)) {
            return;
        }
        String taskId = memoryService.currentTask(sessionId);
        ChatFlowState pending = taskId == null ? null : checkpointService.load(taskId);
if (pending != null && pending.getContextJson() != null
&& !pending.getContextJson().trim().isEmpty() && !"{}".equals(pending.getContextJson().trim())) {
state.setContextJson(pending.getContextJson());
// 旧任务的人工上下文提示词轨迹一并过继拼接：同一创作意图的累积提示词不丢
String pendingTrail = agentMemory.readPromptTrail(taskId);
if (pendingTrail != null && !pendingTrail.trim().isEmpty()) {
agentMemory.appendPromptTrail(state.getThreadId(), pendingTrail);
}
}
    }

    /**
     * 新编排开始前释放上一任务的残留任务态：旧任务 id 的 checkpoint/隔离记忆、
     * 未被处理的旧选项卡挂起、任务映射。任务 id 与会话 id 分离，每次创作一个全新任务，
     * 顺带避免「挂起期间直接发新消息 → 旧挂起被超时兑底重复放行」的边界问题。
     */
    private void releaseStaleTask(Long sessionId) {
        memoryService.takePendingOption(sessionId); // 丢弃未被处理的旧选项卡挂起
        memoryService.clearPending(sessionId);
        String oldTaskId = memoryService.currentTask(sessionId);
        if (oldTaskId != null) {
            checkpointService.delete(oldTaskId);
            agentMemory.clearThread(oldTaskId);
        }
        // 兼容旧版本按会话 id 作任务 id 的残留键（升级过渡期清理）
        checkpointService.delete(String.valueOf(sessionId));
        agentMemory.clearThread(String.valueOf(sessionId));
        memoryService.releaseTask(sessionId);
    }

    private void runGraph(ChatFlowState state, NodeListener<ChatFlowState> listener) {
        if (listener == null) {
            chatFlowGraph.run(state);
        } else {
            chatFlowGraph.run(state, listener);
        }
    }

    private ChatMessage message(Long tenantId, ChatSession session, String role, String content) {
        ChatMessage message = new ChatMessage();
        message.setTenantId(tenantId);
        message.setSessionId(session.getId());
        message.setUserId(session.getUserId());
        message.setRole(role);
        message.setContent(content);
        return message;
    }
}
