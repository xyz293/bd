package com.xiaoa.ai.chat.agent;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;

/**
 * Agent 隔离记忆（Redis Hash）：每个 Agent 独立命名空间，只存自己业务逻辑需要的中间产物。
 *
 * <p>键设计：{@code chat:agent:{threadId}:{agentName}} Hash，TTL 2h（与会话记忆一致）。
 * 例如 GateAgent 的判断轮次历史存在 {@code chat:agent:1:gateAgent}，
 * SkillAgent 的调用轨迹存在 {@code chat:agent:1:skillAgent}，彼此不可见。</p>
 *
 * <p>与 {@code ChatMemoryService}（会话级共享记忆）和 {@code ChatCheckpointService}
 * （整图状态快照）互补：本类只做 Agent 私有工作记忆。</p>
 */
@Service
public class AgentMemoryService {

    /** 记忆 TTL：2 小时，交互续期 */
    private static final Duration MEMORY_TTL = Duration.ofHours(2);
    private static final String KEY_PREFIX = "chat:agent:";

    private final StringRedisTemplate redisTemplate;

    public AgentMemoryService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** 写本 Agent 记忆字段。 */
    public void write(String threadId, String agentName, String field, String value) {
        if (isBlank(threadId) || isBlank(agentName) || isBlank(field) || value == null) {
            return;
        }
        redisTemplate.opsForHash().put(key(threadId, agentName), field, value);
        redisTemplate.expire(key(threadId, agentName), MEMORY_TTL);
    }

    /** 读本 Agent 记忆字段，miss 返回 null。 */
    public String read(String threadId, String agentName, String field) {
        if (isBlank(threadId) || isBlank(agentName) || isBlank(field)) {
            return null;
        }
        Object value = redisTemplate.opsForHash().get(key(threadId, agentName), field);
        if (value != null) {
            redisTemplate.expire(key(threadId, agentName), MEMORY_TTL);
        }
        return value == null ? null : String.valueOf(value);
    }

    /** 本 Agent 记忆全量快照（观测/调试用）。 */
    public Map<Object, Object> snapshot(String threadId, String agentName) {
        return redisTemplate.opsForHash().entries(key(threadId, agentName));
    }

    /** 清除本 Agent 记忆（会话关闭等清理场景）。 */
    public void clear(String threadId, String agentName) {
        if (isBlank(threadId) || isBlank(agentName)) {
            return;
        }
        redisTemplate.delete(key(threadId, agentName));
    }

    private String key(String threadId, String agentName) {
        return KEY_PREFIX + threadId + ":" + agentName;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
