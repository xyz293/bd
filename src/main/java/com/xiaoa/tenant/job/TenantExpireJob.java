package com.xiaoa.tenant.job;

import com.xiaoa.common.lock.RedisDistributedLock;
import com.xiaoa.tenant.mapper.TenantMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Component
public class TenantExpireJob {

    /** 扫描级锁：批量 UPDATE 本身幂等，加锁仅为避免多实例重复扫描。 */
    private static final String JOB_LOCK_KEY = "job:tenant-expire";

    private final TenantMapper tenantMapper;
    private final RedisDistributedLock distributedLock;

    public TenantExpireJob(TenantMapper tenantMapper, RedisDistributedLock distributedLock) {
        this.tenantMapper = tenantMapper;
        this.distributedLock = distributedLock;
    }

    @Scheduled(cron = "0 10 0 * * ?")
    public void markExpired() {
        String requestId = distributedLock.newRequestId();
        if (!distributedLock.tryLock(JOB_LOCK_KEY, requestId, 0, 10, TimeUnit.MINUTES)) {
            return;
        }
        try {
            tenantMapper.markExpired(LocalDateTime.now());
        } finally {
            distributedLock.unlock(JOB_LOCK_KEY, requestId);
        }
    }
}
