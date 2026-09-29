package com.xiaoa.common.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.lang.NonNull;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * WebSocket 连接封装：统一 JSON 帧发送、并发写保护与安全关闭。
 *
 * <p>{@link WebSocketSession} 非线程安全（并发 sendMessage 会抛
 * {@code TEXT_PARTIAL_WRITING}），所有发送经内部锁串行化；
 * 发送失败（连接已断）后通道置为关闭态，后续发送静默忽略，不再抛出异常。</p>
 *
 * <p>帧协议约定（JSON，服务端统一补 {@code type} 字段）：</p>
 * <ul>
 *   <li>{@code stage}   — 过程进度事件</li>
 *   <li>{@code message} — 最终业务结果</li>
 *   <li>{@code done}    — 正常结束</li>
 *   <li>{@code error}   — 业务/系统异常（code + message）</li>
 *   <li>{@code pong}    — 心跳应答</li>
 * </ul>
 */
public class WebSocketChannel {

    private final WebSocketSession session;
    private final ObjectMapper objectMapper;
    private final Object sendLock = new Object();
    private volatile boolean closed = false;

    public WebSocketChannel(@NonNull WebSocketSession session, @NonNull ObjectMapper objectMapper) {
        this.session = session;
        this.objectMapper = objectMapper;
    }

    /** WebSocket 会话 ID（一条连接一个 ID，与业务 sessionId 无关）。 */
    public @NonNull String channelId() {
        return session.getId();
    }

    /** 连接是否可用。 */
    public boolean isOpen() {
        return !closed && session.isOpen();
    }

    /**
     * 发送一帧 JSON 消息：在 payload 外层补充 {@code type} 字段。
     * 连接已关闭或发送失败时静默忽略。
     *
     * @param type    帧类型（stage/message/done/error/pong…）
     * @param payload 业务负载（可为 null）
     */
    public void send(@NonNull String type, Map<String, Object> payload) {
        if (closed || !session.isOpen()) {
            return;
        }
        Map<String, Object> frame = new LinkedHashMap<String, Object>();
        frame.put("type", type);
        if (payload != null) {
            frame.putAll(payload);
        }
        try {
            synchronized (sendLock) {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(frame)));
            }
        } catch (IOException | IllegalStateException exception) {
            // 发送失败视为连接已断，置为关闭态；后续发送静默忽略
            closed = true;
        }
    }

    /** 安全关闭连接（幂等）。 */
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            session.close(CloseStatus.NORMAL);
        } catch (Exception ignored) {
            // 连接可能已被容器关闭
        }
    }
}
