package com.xiaoa.admin.model;

public class AuditConfig {

    private final Long orgId;
    private final boolean enabled;

    public AuditConfig(Long orgId, boolean enabled) {
        this.orgId = orgId;
        this.enabled = enabled;
    }

    public Long getOrgId() { return orgId; }
    public boolean isEnabled() { return enabled; }
}
