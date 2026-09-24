package com.xiaoa.ai.service;

import com.xiaoa.admin.mapper.AuditConfigMapper;
import org.springframework.stereotype.Service;

/**
 * 审核开关读取（组织级覆盖品牌级 org_id=0）。
 * 供生成成功结算等无登录态的后台线程使用，不做权限校验。
 */
@Service
public class AuditConfigService {

    private final AuditConfigMapper configMapper;

    public AuditConfigService(AuditConfigMapper configMapper) {
        this.configMapper = configMapper;
    }

    public boolean enabledFor(Long tenantId, Long orgId) {
        if (tenantId == null) {
            return false;
        }
        Long target = orgId == null ? 0L : orgId;
        Integer enabled = configMapper.findEnabled(tenantId, target);
        if (enabled == null && target != 0L) {
            enabled = configMapper.findEnabled(tenantId, 0L);
        }
        return enabled != null && enabled == 1;
    }
}
