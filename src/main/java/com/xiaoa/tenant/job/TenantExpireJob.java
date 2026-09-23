package com.xiaoa.tenant.job;

import com.xiaoa.tenant.mapper.TenantMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class TenantExpireJob {

    private final TenantMapper tenantMapper;

    public TenantExpireJob(TenantMapper tenantMapper) {
        this.tenantMapper = tenantMapper;
    }

    @Scheduled(cron = "0 10 0 * * ?")
    public void markExpired() {
        tenantMapper.markExpired(LocalDateTime.now());
    }
}
