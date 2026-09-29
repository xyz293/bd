package com.xiaoa.common.websocket;

import com.xiaoa.common.auth.AuthPrincipal;
import com.xiaoa.common.auth.SessionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

/**
 * WebSocket 握手鉴权：复用与 REST 相同的 token 会话校验
 * （{@link SessionService#get}），把 {@link AuthPrincipal} 放入
 * WebSocketSession attributes，供业务 Handler 直接取用。
 *
 * <p>token 通过连接地址的 query 参数传递（浏览器 WebSocket API 无法自定义 Header）：
 * {@code ws://host/ws/chat?token=xxx}；token 缺失或无效时拒绝握手（401）。</p>
 */
public class HandshakeAuthInterceptor implements HandshakeInterceptor {

    /** WebSocketSession attributes 中 principal 的 key */
    public static final String ATTR_PRINCIPAL = "principal";

    private final SessionService sessionService;

    public HandshakeAuthInterceptor(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @Override
    public boolean beforeHandshake(@NonNull ServerHttpRequest request, @NonNull ServerHttpResponse response,
                                   @NonNull WebSocketHandler wsHandler, @NonNull Map<String, Object> attributes) {
        String token = UriComponentsBuilder.fromUri(request.getURI()).build()
                .getQueryParams().getFirst("token");
        if (!StringUtils.hasText(token)) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        AuthPrincipal principal = sessionService.get(token.trim());
        if (principal == null) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        attributes.put(ATTR_PRINCIPAL, principal);
        return true;
    }

    @Override
    public void afterHandshake(@NonNull ServerHttpRequest request, @NonNull ServerHttpResponse response,
                               @NonNull WebSocketHandler wsHandler, @Nullable Exception exception) {
        // 无需处理
    }
}
