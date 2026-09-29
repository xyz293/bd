package com.xiaoa.ai.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoa.ai.chat.dto.ChatAnswerRequest;
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
import com.xiaoa.common.auth.AuthContext;
import com.xiaoa.common.auth.AuthPrincipal;
import com.xiaoa.common.context.TenantContext;
import com.xiaoa.common.exception.BusinessException;
import com.xiaoa.common.exception.ErrorCode;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 对话编排入口（方案 §9 接口契约的四个入口）：会话鉴权、落用户消息、事务边界。
 *
 * <ul>
 *   <li>{@link #chat}：用户消息入口（POST /chat/message / WS chat.send）</li>
 *   <li>{@link #answer}：问卷作答提交（挂起点1 恢复，POST /chat/answer / WS chat.answer）</li>
 *   <li>{@link #option}：选项卡选择（挂起点2 恢复，POST /chat/option / WS chat.option）</li>
 *   <li>{@link #releaseOptionTimeout}：选项卡超时兜底放行（调度线程调用，默认 D 直接生成）</li>
 * </ul>
 *
 * <p>具体流程编排（问卷/选项卡/核验预扣/生成/合规/计费/落库）由状态图 {@link ChatFlowGraph}
 * 驱动：上下文装入 {@link ChatFlowState} 后执行整张图并取回 reply。预扣（hold）在事务内，
 * 任何失败异常穿透图执行整体回滚（额度自动释放，消息不落，员工可重发）。</p>
 */
@Service
public class ChatFlowService {

    private static final String PENDING_QUESTIONNAIRE = "QUESTIONNAIRE";
    private static final String PENDING_OPTION_CARD = "OPTION_CARD";

    private final ChatSessionService sessionService;
    private final ChatMessageMapper messageMapper;
    private final ChatMemoryService memoryService;
    private final ChatFlowGraph chatFlowGraph;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ChatFlowService(ChatSessionService sessionService, ChatMessageMapper messageMapper,
                           ChatMemoryService memoryService, ChatFlowGraph chatFlowGraph) {
        this.sessionService = sessionService;
        this.messageMapper = messageMapper;
        this.memoryService = memoryService;
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

        // 1. 存用户消息（任何后续失败随事务回滚，员工可重发）
        ChatMessage userMessage = message(principal.getTenantId(), session, ChatMessage.ROLE_USER, request.getText());
        messageMapper.insert(userMessage);

        // 2. 执行对话状态图：①获取数据 → ②理解意图 → ③问卷(挂起) / ④核验预扣 → ⑤选项卡(挂起)
        //    → ⑥生成 → ⑦审查回复 → 落库
        ChatFlowState state = ChatFlowState.forChat(principal, session, request.getText());
        runGraph(state, listener);
        return state.getReply();
    }

    // ==================== 问卷作答提交（挂起点1 恢复） ====================

    /** 校验挂起 → 答案合并回 ② 重算缺口（轮次 +1，≤2 轮后仍缺由 AI 代选）。 */
    @Transactional
    public ChatReplyVO answer(AuthPrincipal principal, Long sessionId, ChatAnswerRequest request) {
        ChatSession session = sessionService.requireUsable(principal, sessionId);
        if (!memoryService.isPending(sessionId, PENDING_QUESTIONNAIRE)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "当前没有待回答的问卷");
        }
        Map<String, String> answers = new LinkedHashMap<String, String>();
        for (ChatAnswerRequest.AnswerItem item : request.getAnswers()) {
            answers.put(item.getSlotKey(), item.getValue());
        }
        ChatMessage userMessage = message(principal.getTenantId(), session, ChatMessage.ROLE_USER,
                answerJson(request));
        messageMapper.insert(userMessage);

        ChatFlowState state = ChatFlowState.forAnswer(principal, session, answers);
        chatFlowGraph.run(state);
        return state.getReply();
    }

    // ==================== 选项卡选择（挂起点2 恢复） ====================

    /** 抢占挂起（与超时兜底任务竞争，被抢走说明已默认放行）→ 应用增益槽位 → 直进 ⑥。 */
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

        ChatFlowState state = ChatFlowState.forOption(principal, session, request.getKey());
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
            ChatFlowState state = ChatFlowState.forTimeout(principal, session);
            chatFlowGraph.run(state);
            memoryService.track(pending.getTenantId(), "option_card_timeout");
        } finally {
            AuthContext.clear();
            TenantContext.clear();
        }
    }

    // ==================== 微调入口 ====================

    @Transactional
    public ChatReplyVO revise(AuthPrincipal principal, Long sessionId, ChatReviseRequest request) {
        ChatSession session = sessionService.requireUsable(principal, sessionId);

        // 执行微调状态图：校验版本 → LLM 改写 → 合规 → 计费 → 落库
        ChatFlowState state = ChatFlowState.forRevise(principal, session, request);
        chatFlowGraph.run(state);
        return state.getReply();
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

    /** 用户作答消息 JSON（落库留痕 + 埋点原料）。 */
    private String answerJson(ChatAnswerRequest request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (Exception exception) {
            return "{\"type\":\"questionnaire_answer\"}";
        }
    }
}
