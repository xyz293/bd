package com.xiaoa.common.lock;

import com.xiaoa.common.exception.BusinessException;
import com.xiaoa.common.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 基于 Redis 的分布式锁封装。
 *
 * <p>实现原理：</p>
 * <ul>
 *   <li>加锁：{@code SET lock:{key} requestId NX PX leaseTime}，单命令原子加锁，value 记录持有者标识；</li>
 *   <li>解锁：Lua 脚本「校验 value 等于自己的 requestId 才 DEL」，保证校验与删除原子执行，
 *       避免锁过期后误删其他持有者的锁；</li>
 *   <li>续期：Lua 脚本「校验 value 后 PEXPIRE」，供耗时不可控的业务在执行中手动续期。</li>
 * </ul>
 *
 * <p>与 {@code SessionService} 的本地降级策略不同：Redis 不可用时锁操作直接抛错失败，
 * 绝不降级为「无锁执行」，避免并发写坏数据。</p>
 *
 * <p>推荐用法一（手动控制，需要自行 try/finally）：</p>
 * <pre>{@code
 * String requestId = distributedLock.newRequestId();
 * if (distributedLock.tryLock("export:" + tenantId, requestId, 0, 30, TimeUnit.SECONDS)) {
 *     try {
 *         // 业务逻辑
 *     } finally {
 *         distributedLock.unlock("export:" + tenantId, requestId);
 *     }
 * } else {
 *     throw new BusinessException(ErrorCode.DUPLICATE, "任务处理中，请稍后");
 * }
 * }</pre>
 *
 * <p>推荐用法二（函数式，自动加锁与释放，获取锁失败抛 {@link ErrorCode#SYSTEM_ERROR}）：</p>
 * <pre>{@code
 * distributedLock.execute("export:" + tenantId, 0, 30, TimeUnit.SECONDS, () -> {
 *     // 业务逻辑
 * });
 * }</pre>
 */
@Component
public class RedisDistributedLock {

    private static final Logger log = LoggerFactory.getLogger(RedisDistributedLock.class);

    /** 业务 key 统一前缀，与 session: 等既有前缀风格一致。 */
    private static final String KEY_PREFIX = "lock:";

    /** 自旋重试基础间隔（毫秒），叠加随机抖动防止惊群。 */
    private static final long SPIN_INTERVAL_MILLIS = 50L;
    private static final long SPIN_JITTER_MILLIS = 50L;

    private final StringRedisTemplate redisTemplate;

    /** 解锁脚本：value 匹配才删除，返回 1=解锁成功，0=锁已过期或非本人持有。 */
    private static final @NonNull RedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) "
                    + "else return 0 end", Long.class);

    /** 续期脚本：value 匹配才重置过期时间，返回 1=续期成功，0=锁已不属于本人。 */
    private static final @NonNull RedisScript<Long> RENEW_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('PEXPIRE', KEYS[1], ARGV[2]) "
                    + "else return 0 end", Long.class);

    public RedisDistributedLock(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** 生成锁持有者标识（每次加锁应使用新的 requestId）。 */
    public @NonNull String newRequestId() {
        return Objects.requireNonNull(UUID.randomUUID().toString().replace("-", ""));
    }

    /**
     * 单次尝试加锁，不等待。
     *
     * @param key       业务锁 key（自动加 lock: 前缀），如 {@code export:1}
     * @param requestId 持有者标识，解锁时必须传同一个值
     * @param leaseTime 锁自动过期时间，必须大于 0（防死锁兜底）
     * @param unit      时间单位
     * @return true=加锁成功；false=锁被占用
     */
    public boolean tryLock(@NonNull String key, @NonNull String requestId, long leaseTime, @NonNull TimeUnit unit) {
        validate(key, requestId);
        requirePositiveLease(leaseTime);
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX + key, requestId, leaseTime, unit);
        return Boolean.TRUE.equals(acquired);
    }

    /**
     * 限时等待加锁：在 waitTime 内自旋重试，超时返回 false（不抛异常）。
     *
     * <p>waitTime 传 0 等价于单次尝试。被中断时恢复中断标记并返回 false。</p>
     */
    public boolean tryLock(@NonNull String key, @NonNull String requestId, long waitTime, long leaseTime,
                           @NonNull TimeUnit unit) {
        validate(key, requestId);
        requirePositiveLease(leaseTime);
        if (waitTime <= 0) {
            return tryLock(key, requestId, leaseTime, unit);
        }
        long deadline = System.currentTimeMillis() + unit.toMillis(waitTime);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        while (true) {
            if (tryLock(key, requestId, leaseTime, unit)) {
                return true;
            }
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) {
                return false;
            }
            long sleepMillis = Math.min(SPIN_INTERVAL_MILLIS + random.nextLong(SPIN_JITTER_MILLIS), remaining);
            try {
                TimeUnit.MILLISECONDS.sleep(sleepMillis);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                log.warn("Interrupted while waiting for lock: {}", key);
                return false;
            }
        }
    }

    /**
     * 解锁：Lua 脚本校验 requestId 后删除。
     *
     * @return true=解锁成功；false=锁已过期被其他实例持有，或 Redis 异常（仅记录日志，不抛出，
     *         方便在 finally 中安全调用而不吞掉业务异常）
     */
    public boolean unlock(@NonNull String key, @NonNull String requestId) {
        validate(key, requestId);
        try {
            Long result = redisTemplate.execute(UNLOCK_SCRIPT,
                    Objects.requireNonNull(Collections.singletonList(KEY_PREFIX + key)), requestId);
            boolean released = result != null && result == 1L;
            if (!released) {
                log.warn("Unlock skipped, lock not held by current request. key={}", key);
            }
            return released;
        } catch (RuntimeException exception) {
            log.error("Unlock failed due to redis error. key={}", key, exception);
            return false;
        }
    }

    /**
     * 锁续期：仅当仍由当前 requestId 持有时重置过期时间。
     *
     * @return true=续期成功；false=锁已过期或被他人持有
     */
    public boolean renew(@NonNull String key, @NonNull String requestId, long leaseTime, @NonNull TimeUnit unit) {
        validate(key, requestId);
        requirePositiveLease(leaseTime);
        Long result = redisTemplate.execute(RENEW_SCRIPT,
                Objects.requireNonNull(Collections.singletonList(KEY_PREFIX + key)), requestId,
                String.valueOf(unit.toMillis(leaseTime)));
        return result != null && result == 1L;
    }

    /**
     * 函数式加锁执行：成功获取锁后执行业务，finally 中自动解锁；获取锁失败抛 {@link BusinessException}。
     *
     * @param waitTime 获取锁最长等待时间（0 表示只尝试一次）
     * @param leaseTime 锁自动过期时间
     */
    public void execute(@NonNull String key, long waitTime, long leaseTime, @NonNull TimeUnit unit,
                        @NonNull Runnable action) {
        execute(key, waitTime, leaseTime, unit, () -> {
            action.run();
            return null;
        });
    }

    /**
     * 同上，带返回值版本。
     */
    public <T> T execute(@NonNull String key, long waitTime, long leaseTime, @NonNull TimeUnit unit,
                         @NonNull Supplier<T> action) {
        String requestId = newRequestId();
        if (!tryLock(key, requestId, waitTime, leaseTime, unit)) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "获取分布式锁超时: " + key);
        }
        try {
            return action.get();
        } finally {
            unlock(key, requestId);
        }
    }

    private void validate(String key, String requestId) {
        if (!StringUtils.hasText(key)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "分布式锁 key 不能为空");
        }
        if (!StringUtils.hasText(requestId)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "分布式锁 requestId 不能为空");
        }
    }

    private void requirePositiveLease(long leaseTime) {
        if (leaseTime <= 0) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "分布式锁租期必须大于0");
        }
    }
}
