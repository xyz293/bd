package com.xiaoa.ai.chat.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoa.ai.chat.graph.ChatFlowState;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 图状态短期记忆（LangGraph checkpoint 语义）：
 * <b>仅在选项卡挂起（编排暂停等用户选择）时保存</b>完整 {@link ChatFlowState}；
 * 第二次请求（选项卡选择 / 超时兜底）按任务 id（与会话 id 分离，
 * 经「会话 → 任务 id」映射定位）取回「停止之前的记忆」续跑。
 * 作品生成等终结态不再保存（编排出口会顺带释放任务态），任务态不跨创作复用。
 *
 * <p>键设计：{@code chat:ckpt:{threadId}}，TTL 2h（与会话记忆一致）。</p>
 *
 * <p>降级语义：checkpoint 写入/读取失败只打日志不抛错——恢复入口取不到时
 * 回退到重建式装配（forOption/forTimeout），链路可用性不受影响。</p>
 */
@Service
public class ChatCheckpointService {

    /** 短期记忆 TTL：2 小时（与 ChatMemoryService 会话记忆一致） */
    private static final Duration CHECKPOINT_TTL = Duration.ofHours(2);
    private static final String KEY_PREFIX = "chat:ckpt:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public ChatCheckpointService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /** 保存/覆盖 thread 的最新状态快照（threadId 即会话 ID 字符串）。 */
    public void save(ChatFlowState state) {
        if (state == null || state.getThreadId() == null || state.getThreadId().trim().isEmpty()) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + state.getThreadId(),
                    objectMapper.writeValueAsString(state), CHECKPOINT_TTL);
        } catch (Exception ignored) {
            // checkpoint 失败不阻塞主流程：恢复时回退重建式装配
        }
    }

    /** 按 thread id 取回停止前的状态；miss/损坏返回 null（调用方回退重建）。 */
    public ChatFlowState load(String threadId) {
        if (threadId == null || threadId.trim().isEmpty()) {
            return null;
        }
        try {
            String json = redisTemplate.opsForValue().get(KEY_PREFIX + threadId);
            if (json == null || json.trim().isEmpty()) {
                return null;
            }
            return objectMapper.readValue(json, ChatFlowState.class);
        } catch (Exception ignored) {
            // 快照损坏视为无记忆，回退重建式装配
            return null;
        }
    }

    /** 删除 thread 的短期记忆（会话关闭等清理场景）。 */
    public void delete(String threadId) {
        if (threadId == null || threadId.trim().isEmpty()) {
            return;
        }
        redisTemplate.delete(KEY_PREFIX + threadId);
    }
}
