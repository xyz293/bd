package com.xiaoa.ai.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoa.ai.chat.dto.PendingOption;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 会话记忆（方案 ① fetch_context / ⑦ 存记忆）：Redis Hash 存储，TTL 2h 交互续期。
 *
 * <p>键设计：</p>
 * <ul>
 *   <li>{@code chat:mem:{sessionId}}           Hash：slots（槽位快照 JSON）/ round（问卷轮次）/ pendingType</li>
 *   <li>{@code chat:pending:option:{sessionId}} String：选项卡挂起载荷 JSON（供超时兜底扫描）</li>
 *   <li>{@code chat:seq:{sessionId}}            String：INCR 会话序号（预扣幂等键）</li>
 *   <li>{@code chat:metric:{tenantId}:{event}:{yyyyMMdd}} String：INCR 埋点计数（TTL 7d，数据飞轮原料）</li>
 * </ul>
 */
@Service
public class ChatMemoryService {

    /** 记忆 TTL：2 小时，交互续期（方案 §5） */
    private static final Duration MEMORY_TTL = Duration.ofHours(2);
    /** 选项卡挂起键 TTL（兜底上限，防止悬空键） */
    private static final Duration PENDING_TTL = Duration.ofMinutes(10);
    /** 埋点计数 TTL */
    private static final Duration METRIC_TTL = Duration.ofDays(7);

    private static final String KEY_MEM = "chat:mem:";
    private static final String KEY_PENDING_OPTION = "chat:pending:option:";
    private static final String KEY_SEQ = "chat:seq:";
    private static final String KEY_METRIC = "chat:metric:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ChatMemoryService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // ==================== 槽位 / 轮次（会话记忆） ====================

    /** 读槽位快照（fetchContext 用），miss 返回 null（调用方回退 MySQL 会话上下文）。 */
    public String loadSlots(Long sessionId) {
        String slots = (String) redisTemplate.opsForHash().get(keyMem(sessionId), "slots");
        if (slots != null) {
            redisTemplate.expire(keyMem(sessionId), MEMORY_TTL);
        }
        return slots;
    }

    /** 写槽位快照（persist 时与 MySQL 同步）。 */
    public void saveSlots(Long sessionId, String slotsJson) {
        redisTemplate.opsForHash().put(keyMem(sessionId), "slots", slotsJson == null ? "{}" : slotsJson);
        redisTemplate.expire(keyMem(sessionId), MEMORY_TTL);
    }

    /** 读问卷轮次（已消耗轮数），miss 返回 0。 */
    public int loadQuestionnaireRound(Long sessionId) {
        String round = (String) redisTemplate.opsForHash().get(keyMem(sessionId), "round");
        try {
            return round == null ? 0 : Integer.parseInt(round);
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    /** 写问卷轮次。 */
    public void saveQuestionnaireRound(Long sessionId, int round) {
        redisTemplate.opsForHash().put(keyMem(sessionId), "round", String.valueOf(round));
        redisTemplate.expire(keyMem(sessionId), MEMORY_TTL);
    }

    // ==================== 挂起状态（问卷 / 选项卡） ====================

    /** 记录挂起类型（QUESTIONNAIRE / OPTION_CARD），供恢复入口校验。 */
    public void markPending(Long sessionId, String pendingType) {
        redisTemplate.opsForHash().put(keyMem(sessionId), "pendingType", pendingType);
        redisTemplate.expire(keyMem(sessionId), MEMORY_TTL);
    }

    /** 校验挂起类型。 */
    public boolean isPending(Long sessionId, String pendingType) {
        String pending = (String) redisTemplate.opsForHash().get(keyMem(sessionId), "pendingType");
        return pendingType.equals(pending);
    }

    /** 清除挂起类型。 */
    public void clearPending(Long sessionId) {
        redisTemplate.opsForHash().delete(keyMem(sessionId), "pendingType");
    }

    /**
     * 挂起选项卡（方案 ⑤）：写恢复载荷，供 /chat/option 恢复与超时兜底扫描。
     */
    public void holdPendingOption(@NonNull PendingOption pending) {
        try {
            String payload = objectMapper.writeValueAsString(pending);
            redisTemplate.opsForValue().set(keyPendingOption(pending.getSessionId()), payload, PENDING_TTL);
        } catch (Exception ignored) {
            // 序列化失败仅影响超时兜底，用户主动选择不受影响
        }
    }

    /**
     * 抢占式读取挂起选项卡（原子：删除成功才视为占有），
     * /chat/option 与超时兜底任务双方通过 DEL 竞争，防止重复放行。
     *
     * @return 载荷；返回 null 表示无挂起或已被对方抢占
     */
    public PendingOption takePendingOption(Long sessionId) {
        String key = keyPendingOption(sessionId);
        String payload = redisTemplate.opsForValue().get(key);
        if (payload == null) {
            return null;
        }
        if (Boolean.TRUE.equals(redisTemplate.delete(key))) {
            try {
                return objectMapper.readValue(payload, PendingOption.class);
            } catch (Exception exception) {
                return null;
            }
        }
        return null;
    }

    /** 扫描已超时（超过 deadline + graceMillis）的挂起选项卡，供兜底任务放行。 */
    public List<PendingOption> scanExpiredOptions(long graceMillis) {
        List<PendingOption> expired = new ArrayList<>();
        long now = System.currentTimeMillis();
        Set<String> keys = redisTemplate.keys(KEY_PENDING_OPTION + "*");
        if (keys == null || keys.isEmpty()) {
            return expired;
        }
        for (String key : keys) {
            String payload = redisTemplate.opsForValue().get(key);
            if (payload == null) {
                continue;
            }
            try {
                PendingOption pending = objectMapper.readValue(payload, PendingOption.class);
                if (now - pending.getDeadline() > graceMillis) {
                    expired.add(pending);
                }
            } catch (Exception ignored) {
                // 载荷损坏：直接清理
                redisTemplate.delete(key);
            }
        }
        return expired;
    }

    // ==================== 序号 / 埋点 ====================

    /** 会话级自增序号（预扣幂等键 chat:{sessionId}:hold:{seq}）。 */
    public long nextSeq(Long sessionId) {
        Long seq = redisTemplate.opsForValue().increment(keySeq(sessionId));
        if (seq != null) {
            redisTemplate.expire(keySeq(sessionId), MEMORY_TTL);
            return seq;
        }
        return System.currentTimeMillis() % 1_000_000L;
    }

    /** 选择行为埋点（数据飞轮原料）：问卷作答 / 选项卡跳过率 / 版本采纳等。 */
    public void track(Long tenantId, String event) {
        if (tenantId == null || event == null || event.isEmpty()) {
            return;
        }
        String day = LocalDate.now().toString().replace("-", "");
        String key = KEY_METRIC + tenantId + ":" + event + ":" + day;
        try {
            redisTemplate.opsForValue().increment(key);
            redisTemplate.expire(key, METRIC_TTL);
        } catch (Exception ignored) {
            // 埋点失败不影响主流程
        }
    }

    private String keyMem(Long sessionId) {
        return KEY_MEM + sessionId;
    }

    private String keyPendingOption(Long sessionId) {
        return KEY_PENDING_OPTION + sessionId;
    }

    private String keySeq(Long sessionId) {
        return KEY_SEQ + sessionId;
    }

    /** 供调试/运维：拼装记忆键名。 */
    public static Map<String, String> keySamples(Long sessionId) {
        Map<String, String> samples = new HashMap<String, String>();
        samples.put("mem", KEY_MEM + sessionId);
        samples.put("pending", KEY_PENDING_OPTION + sessionId);
        samples.put("seq", KEY_SEQ + sessionId);
        return samples;
    }
}
