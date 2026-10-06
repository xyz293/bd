package com.xiaoa.ai.chat.agent;

import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Agent 隔离记忆（Redis Hash）：每个 Agent 独立命名空间，只存自己业务逻辑需要的中间产物。
 *
 * <p>键设计：{@code chat:agent:{threadId}:{agentName}} Hash，TTL 2h（与会话记忆一致）。
 * 例如 GateAgent 的判断轮次历史存在 {@code chat:agent:1:gateAgent}，
 * SkillAgent 的调用轨迹存在 {@code chat:agent:1:skillAgent}，彼此不可见。</p>
 *
 * <p>另有一个跨 Agent 共享命名空间 {@link #FLOW_AGENT}：人工上下文提示词轨迹
 * （promptTrail）——员工每次人工更新上下文（选项卡选择/打字补充）时**持续拼接**进任务记忆，
 * 由 ContextLoader 并入上下文后各 Agent 的 LLM 调用都能看到累积的人工更新，不重复问。</p>
 *
 * <p>与 {@code ChatMemoryService}（会话级共享记忆）和 {@code ChatCheckpointService}
 * （整图状态快照）互补：本类只做 Agent 私有工作记忆。</p>
 */
@Service
public class AgentMemoryService {

    /** 跨 Agent 共享命名空间（人工上下文轨迹等流程级记忆） */
    public static final String FLOW_AGENT = "flow";
    /** 人工上下文提示词轨迹字段名 */
    public static final String FIELD_PROMPT_TRAIL = "promptTrail";

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

    // ==================== 人工上下文提示词轨迹（跨 Agent 共享，持续拼接） ====================

    /**
     * 拼接一条人工更新上下文的提示词轨迹（选项卡选择/打字补充等每次人工更新都调）。
     * 以「；」拼接累积在任务记忆里（flow 命名空间），任务终结随 clearThread 一起释放。
     */
    public void appendPromptTrail(String threadId, String update) {
        if (isBlank(threadId) || isBlank(update)) {
            return;
        }
        String existing = read(threadId, FLOW_AGENT, FIELD_PROMPT_TRAIL);
        String merged = isBlank(existing) ? update.trim() : existing + "；" + update.trim();
        write(threadId, FLOW_AGENT, FIELD_PROMPT_TRAIL, merged);
    }

    /** 读人工上下文提示词轨迹（累积拼接后的完整文本），miss 返回 null。 */
    public String readPromptTrail(String threadId) {
        return read(threadId, FLOW_AGENT, FIELD_PROMPT_TRAIL);
    }

    /**
     * 清除整个任务（thread）下所有 Agent 的隔离记忆。
     *
     * <p>作品生成等编排终结场景调用：当前任务 id 的短期记忆使命完成，
     * 下一次创作从全新任务态开始，不再复用旧任务的中间产物
     * （如 Gate 判断轮次、技能调用轨迹）。用 SCAN 逐批匹配避免 KEYS 阻塞。</p>
     */
    public void clearThread(String threadId) {
        if (isBlank(threadId)) {
            return;
        }
        try (Cursor<String> cursor = redisTemplate.scan(
                ScanOptions.scanOptions().match(KEY_PREFIX + threadId + ":*").count(100).build())) {
            List<String> keys = new ArrayList<String>();
            while (cursor.hasNext()) {
                keys.add(cursor.next());
            }
            if (!keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception ignored) {
            // 清理失败不影响主流程：记忆带 TTL 会自然过期
        }
    }

    private String key(String threadId, String agentName) {
        return KEY_PREFIX + threadId + ":" + agentName;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
