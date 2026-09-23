package com.xiaoa.tenant.dto;

public class OpenTenantResponse {

    private final Long tenantId;
    private final Long orgId;
    private final Long userId;

    public OpenTenantResponse(Long tenantId, Long orgId, Long userId) {
        this.tenantId = tenantId;
        this.orgId = orgId;
        this.userId = userId;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public Long getOrgId() {
        return orgId;
    }

    public Long getUserId() {
        return userId;
    }
}
