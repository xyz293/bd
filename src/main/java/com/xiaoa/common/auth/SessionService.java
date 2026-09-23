package com.xiaoa.common.auth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);
    private static final String KEY_PREFIX = "session:";
    private static final Duration SESSION_TTL = Duration.ofDays(7);

    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;
    private final Map<String, String> localSessions = new ConcurrentHashMap<>();

    public SessionService(ObjectMapper objectMapper, ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplateProvider.getIfAvailable();
    }

    public String create(AuthPrincipal principal) {
        String token = UUID.randomUUID().toString().replace("-", "");
        String value = toJson(principal);
        try {
            if (redisTemplate != null) {
                Duration sessionTtl = Objects.requireNonNull(SESSION_TTL);
                redisTemplate.opsForValue().set(KEY_PREFIX + token, value, sessionTtl);
                return token;
            }
        } catch (RuntimeException exception) {
            log.warn("Redis session unavailable, fallback to local session", exception);
        }
        localSessions.put(token, value);
        return token;
    }

    public AuthPrincipal get(String token) {
        if (token == null || token.trim().isEmpty()) {
            return null;
        }
        String value = null;
        try {
            if (redisTemplate != null) {
                value = redisTemplate.opsForValue().get(KEY_PREFIX + token);
            }
        } catch (RuntimeException exception) {
            log.warn("Failed to read Redis session", exception);
        }
        if (value == null) {
            value = localSessions.get(token);
        }
        return value == null ? null : fromJson(value);
    }

    public void delete(String token) {
        if (token == null || token.trim().isEmpty()) {
            return;
        }
        localSessions.remove(token);
        try {
            if (redisTemplate != null) {
                redisTemplate.delete(KEY_PREFIX + token);
            }
        } catch (RuntimeException exception) {
            log.warn("Failed to delete Redis session", exception);
        }
    }

    private @NonNull String toJson(AuthPrincipal principal) {
        try {
            return Objects.requireNonNull(objectMapper.writeValueAsString(principal));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法创建登录态", exception);
        }
    }

    private AuthPrincipal fromJson(String value) {
        try {
            return objectMapper.readValue(value, AuthPrincipal.class);
        } catch (JsonProcessingException exception) {
            log.warn("Invalid session payload", exception);
            return null;
        }
    }
}
