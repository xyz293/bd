package com.xiaoa.common.auth;

public class AuthPrincipal {

    private Long userId;
    private Long tenantId;
    private Long orgId;
    private String role;
    private Integer dataScope;

    public AuthPrincipal() {
    }

    public AuthPrincipal(Long userId, Long tenantId, Long orgId, String role, Integer dataScope) {
        this.userId = userId;
        this.tenantId = tenantId;
        this.orgId = orgId;
        this.role = role;
        this.dataScope = dataScope;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public Long getOrgId() {
        return orgId;
    }

    public void setOrgId(Long orgId) {
        this.orgId = orgId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Integer getDataScope() {
        return dataScope;
    }

    public void setDataScope(Integer dataScope) {
        this.dataScope = dataScope;
    }
}
