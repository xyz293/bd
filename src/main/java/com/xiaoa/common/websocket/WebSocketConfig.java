package com.xiaoa.common.websocket;

import com.xiaoa.ai.chat.service.ChatWebSocketHandler;
import com.xiaoa.common.auth.SessionService;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 端点注册：对话流式通道 {@code /ws/chat}。
 *
 * <p>握手时经 {@link HandshakeAuthInterceptor} 复用 REST 的 token 会话校验，
 * token 无效直接 401 拒绝握手；连接建立后前端帧协议见 {@link ChatWebSocketHandler}。</p>
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    /** 对话流式通道端点 */
    public static final String CHAT_ENDPOINT = "/ws/chat";

    private final ChatWebSocketHandler chatWebSocketHandler;
    private final SessionService sessionService;

    public WebSocketConfig(ChatWebSocketHandler chatWebSocketHandler, SessionService sessionService) {
        this.chatWebSocketHandler = chatWebSocketHandler;
        this.sessionService = sessionService;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(chatWebSocketHandler, CHAT_ENDPOINT)
                .addInterceptors(new HandshakeAuthInterceptor(sessionService))
                .setAllowedOrigins("*");
    }
}
