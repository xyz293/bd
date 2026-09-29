package com.xiaoa.ai.chat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoa.ai.chat.dto.ChatAnswerRequest;
import com.xiaoa.ai.chat.dto.ChatOptionRequest;
import com.xiaoa.ai.chat.dto.ChatSendRequest;
import com.xiaoa.ai.graph.NodeListener;
import com.xiaoa.ai.chat.graph.ChatFlowState;
import com.xiaoa.common.auth.AuthPrincipal;
import com.xiaoa.common.exception.BusinessException;
import com.xiaoa.common.exception.ErrorCode;
import com.xiaoa.common.websocket.WebSocketChannel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 对话 WebSocket 处理器：把节点编排状态图的执行过程流式推给前端
 * （与同步接口共享 {@link ChatFlowService} 同一套事务与节点）。
 *
 * <p>帧协议（JSON，均带 {@code type} 字段，对齐方案 §9 挂起恢复契约）：</p>
 *
 * <pre>
 * 客户端 → 服务端：
 *   {"type":"chat.send","sessionId":1,"text":"帮我写一条朋友圈文案"}     用户消息入口
 *   {"type":"chat.answer","sessionId":1,"answers":[{"slotKey":"product","value":"对戒"}]}
 *                                                                       问卷作答提交（挂起点1 恢复）
 *   {"type":"chat.option","sessionId":1,"key":"A"}                       选项卡选择（挂起点2 恢复）
 *   {"type":"ping"}                                                      应用层心跳
 *
 * 服务端 → 客户端：
 *   {"type":"connected"} / {"type":"pong"}
 *   {"type":"stage","sessionId":1,"node":"understandIntent","label":"…"}  节点阶段进度
 *   {"type":"message","sessionId":1,"reply":{...ChatReplyVO...}}          节点推进结果（事务提交后发送）
 *        reply.action = QUESTIONNAIRE / OPTION_CARD 时前端渲染挂起 UI 等待用户操作
 *        reply.action = PENDING_MEDIA 时前端拿 workId 轮询作品状态
 *   {"type":"done","sessionId":1} / {"type":"error","sessionId":1,"code":4001,"message":"…"}
 * </pre>
 *
 * <p>执行模型：握手时已由 {@code HandshakeAuthInterceptor} 完成鉴权（principal 在 attributes）；
 * 三类输入帧统一交给 {@code chatStreamExecutor} 异步执行，事务边界在 {@link ChatFlowService}
 * （@Transactional 代理，与 REST 链路完全一致）。同一条连接同一时刻只允许一次进行中的编排，
 * 重复发送直接回 error 帧。</p>
 */
@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    /** attributes 中 WebSocketChannel 的 key */
    private static final String ATTR_CHANNEL = "channel";
    /** 客户端帧类型：发消息 */
    private static final String TYPE_CHAT_SEND = "chat.send";
    /** 客户端帧类型：问卷作答 */
    private static final String TYPE_CHAT_ANSWER = "chat.answer";
    /** 客户端帧类型：选项卡选择 */
    private static final String TYPE_CHAT_OPTION = "chat.option";
    /** 客户端帧类型：心跳 */
    private static final String TYPE_PING = "ping";

    /** 图节点名 -> 前端阶段文案（stage 帧） */
    private static final Map<String, String> NODE_LABELS = buildNodeLabels();

    private final ObjectMapper objectMapper;
    private final ChatSessionService sessionService;
    private final ChatFlowService flowService;
    private final Executor chatStreamExecutor;

    /** 每条连接的进行中编排标志（key = WebSocketSession.getId()），防止并发执行导致上下文交错 */
    private final ConcurrentHashMap<String, AtomicBoolean> busyMap = new ConcurrentHashMap<String, AtomicBoolean>();

    public ChatWebSocketHandler(ObjectMapper objectMapper,
                                ChatSessionService sessionService,
                                ChatFlowService flowService,
                                @Qualifier("chatStreamExecutor") Executor chatStreamExecutor) {
        this.objectMapper = objectMapper;
        this.sessionService = sessionService;
        this.flowService = flowService;
        this.chatStreamExecutor = chatStreamExecutor;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        WebSocketChannel channel = new WebSocketChannel(session, objectMapper);
        session.getAttributes().put(ATTR_CHANNEL, channel);
        busyMap.put(session.getId(), new AtomicBoolean(false));
        channel.send("connected", null);
    }

    @Override
    protected void handleTextMessage(@NonNull WebSocketSession session, @NonNull TextMessage message) {
        WebSocketChannel channel = channelOf(session);
        if (channel == null) {
            return;
        }
        JsonNode frame = parse(message.getPayload());
        String type = frame.path("type").asText("");
        if (TYPE_PING.equals(type)) {
            channel.send("pong", null);
            return;
        }
        if (!TYPE_CHAT_SEND.equals(type) && !TYPE_CHAT_ANSWER.equals(type) && !TYPE_CHAT_OPTION.equals(type)) {
            channel.send("error", error(sessionId(frame), ErrorCode.INVALID_PARAMETER.getCode(), "未知消息类型: " + type));
            return;
        }
        dispatch(session, channel, frame, type);
    }

    /** 三类输入帧统一入口：鉴权 → 会话校验 → 串行化 → 异步执行对应编排入口。 */
    private void dispatch(WebSocketSession session, WebSocketChannel channel, JsonNode frame, String type) {
        AuthPrincipal principal = (AuthPrincipal) session.getAttributes()
                .get(com.xiaoa.common.websocket.HandshakeAuthInterceptor.ATTR_PRINCIPAL);
        if (principal == null) {
            channel.send("error", error(sessionId(frame), ErrorCode.UNAUTHORIZED.getCode(),
                    ErrorCode.UNAUTHORIZED.getMessage()));
            return;
        }
        long sessionId = frame.path("sessionId").asLong(0L);
        if (sessionId <= 0) {
            channel.send("error", error(0L, ErrorCode.INVALID_PARAMETER.getCode(), "sessionId 不能为空"));
            return;
        }
        // 会话可用性校验（本人/未关闭/7 天活跃）在 WebSocket 容器线程完成
        try {
            sessionService.requireUsable(principal, sessionId);
        } catch (BusinessException exception) {
            channel.send("error", error(sessionId, exception.getCode(), exception.getMessage()));
            return;
        }

        // 同一连接串行化：上一次编排未结束时拒绝，防止会话上下文交错
        AtomicBoolean busy = busyMap.get(session.getId());
        if (busy == null || !busy.compareAndSet(false, true)) {
            channel.send("error", error(sessionId, ErrorCode.SYSTEM_ERROR.getCode(), "上一次创作还在进行中，请稍候"));
            return;
        }
        try {
            chatStreamExecutor.execute(() -> runFlow(principal, sessionId, frame, type, channel, busy));
        } catch (RejectedExecutionException exception) {
            busy.set(false);
            channel.send("error", error(sessionId, ErrorCode.SYSTEM_ERROR.getCode(), "当前创作人数较多，请稍后重试"));
        }
    }

    /** 异步执行编排（复用 Service 事务）：逐节点推 stage，提交后推 message/done。 */
    private void runFlow(final AuthPrincipal principal, final long sessionId, final JsonNode frame,
                         final String type, final WebSocketChannel channel, final AtomicBoolean busy) {
        try {
            com.xiaoa.ai.chat.dto.ChatReplyVO reply;
            if (TYPE_CHAT_SEND.equals(type)) {
                // @Transactional 由 Service 代理开启：用户消息 + 图执行同事务，失败整体回滚
                reply = flowService.chat(principal, sessionId, chatSendOf(frame),
                        (NodeListener<ChatFlowState>) (nodeName, nodeState) ->
                                channel.send("stage", stageEvent(sessionId, nodeName)));
            } else {
                // 问卷作答/选项卡恢复：直接跑到挂起点下游
                reply = invoke(principal, sessionId, frame, type);
            }
            // 事务已提交，推送节点推进结果（问卷/选项卡/生成结果）
            channel.send("message", messageFrame(sessionId, reply));
            channel.send("done", frame(sessionId));
        } catch (BusinessException exception) {
            channel.send("error", error(sessionId, exception.getCode(), exception.getMessage()));
        } catch (RuntimeException exception) {
            channel.send("error", error(sessionId, ErrorCode.SYSTEM_ERROR.getCode(),
                    ErrorCode.SYSTEM_ERROR.getMessage()));
        } finally {
            busy.set(false);
        }
    }

    /** 按帧类型构造请求并调用对应编排入口。 */
    private com.xiaoa.ai.chat.dto.ChatReplyVO invoke(AuthPrincipal principal, long sessionId,
                                                     JsonNode frame, String type) {
        if (TYPE_CHAT_ANSWER.equals(type)) {
            ChatAnswerRequest request = new ChatAnswerRequest();
            List<ChatAnswerRequest.AnswerItem> answers = new ArrayList<>();
            JsonNode answerNodes = frame.path("answers");
            if (answerNodes.isArray()) {
                answerNodes.forEach(item -> {
                    ChatAnswerRequest.AnswerItem answer = new ChatAnswerRequest.AnswerItem();
                    answer.setSlotKey(item.path("slotKey").asText(""));
                    answer.setValue(item.path("value").asText(""));
                    if (!answer.getSlotKey().isEmpty() && !answer.getValue().isEmpty()) {
                        answers.add(answer);
                    }
                });
            }
            request.setAnswers(answers);
            return flowService.answer(principal, sessionId, request);
        }
        if (TYPE_CHAT_OPTION.equals(type)) {
            ChatOptionRequest request = new ChatOptionRequest();
            request.setKey(frame.path("key").asText(""));
            return flowService.option(principal, sessionId, request);
        }
        return flowService.chat(principal, sessionId, chatSendOf(frame), null);
    }

    private ChatSendRequest chatSendOf(JsonNode frame) {
        ChatSendRequest request = new ChatSendRequest();
        request.setText(frame.path("text").asText("").trim());
        return request;
    }

    @Override
    public void handleTransportError(@NonNull WebSocketSession session, @NonNull Throwable exception) {
        // 传输层异常（断网等）：释放进行中标志，连接交给容器关闭流程
        release(session);
    }

    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) {
        release(session);
    }

    private void release(WebSocketSession session) {
        AtomicBoolean busy = busyMap.remove(session.getId());
        if (busy != null) {
            busy.set(false);
        }
    }

    private WebSocketChannel channelOf(WebSocketSession session) {
        Object channel = session.getAttributes().get(ATTR_CHANNEL);
        return channel instanceof WebSocketChannel ? (WebSocketChannel) channel : null;
    }

    private JsonNode parse(String json) {
        try {
            return objectMapper.readTree(json == null || json.trim().isEmpty() ? "{}" : json);
        } catch (Exception exception) {
            return objectMapper.createObjectNode();
        }
    }

    private static Map<String, String> buildNodeLabels() {
        Map<String, String> labels = new LinkedHashMap<String, String>();
        // ① 获取数据
        labels.put("fetchContext", "正在读取创作上下文…");
        // ② 理解意图
        labels.put("understandIntent", "正在理解你的需求…");
        // ③ Gate 充分性判断 + 技能取数
        labels.put("gateAssess", "正在判断信息是否足够…");
        labels.put("skillInvoke", "正在查询创作资料…");
        labels.put("mergeAnswers", "正在合并你的回答…");
        // ④ 核验+扣额度
        labels.put("verifyAndCharge", "正在核验商品信息与额度…");
        labels.put("respondQuota", "正在整理额度提示…");
        // ⑤ 选项卡（信息不足挂起，用户选择后回 Gate 复判）
        labels.put("presentOptionCard", "正在为你准备选项…");
        labels.put("applyOption", "正在应用你的选择…");
        // ⑥ 生成
        labels.put("generateContent", "正在生成内容…");
        labels.put("mediaSubmit", "正在提交图/视频任务…");
        // ⑦ 审查+回复
        labels.put("reviewAndRespond", "正在进行合规检查…");
        labels.put("billingConfirm", "正在记录本次创作…");
        labels.put("respondConsult", "正在回复…");
        // 微调链路
        labels.put("reviseValidate", "正在定位要微调的版本…");
        labels.put("reviseCallLlm", "正在按你的要求改写…");
        labels.put("reviseCompliance", "正在进行合规检查…");
        labels.put("reviseBilling", "正在记录本次微调…");
        // 汇合
        labels.put("responseComposer", "正在组织回复…");
        labels.put("persist", "正在保存会话…");
        return labels;
    }

    private long sessionId(JsonNode frame) {
        return frame.path("sessionId").asLong(0L);
    }

    private Map<String, Object> frame(Long chatSessionId) {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("sessionId", chatSessionId);
        return payload;
    }

    private Map<String, Object> stageEvent(Long chatSessionId, String nodeName) {
        Map<String, Object> payload = frame(chatSessionId);
        payload.put("node", nodeName);
        String label = NODE_LABELS.get(nodeName);
        payload.put("label", label == null ? nodeName : label);
        return payload;
    }

    private Map<String, Object> messageFrame(Long chatSessionId, Object reply) {
        Map<String, Object> payload = frame(chatSessionId);
        payload.put("reply", reply);
        return payload;
    }

    private Map<String, Object> error(long chatSessionId, int code, String message) {
        Map<String, Object> payload = frame(chatSessionId);
        payload.put("code", code);
        payload.put("message", message);
        return payload;
    }
}
