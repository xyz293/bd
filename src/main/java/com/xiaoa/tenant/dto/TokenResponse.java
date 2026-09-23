package com.xiaoa.tenant.dto;

public class TokenResponse {

    private String token;
    private Long userId;
    private Long tenantId;
    private Long orgId;
    private String role;
    private Integer dataScope;
    private Integer tenantStatus;
    private String tenantName;
    private String orgName;

    public TokenResponse() {
    }

    public TokenResponse(String token, Long userId, Long tenantId, Long orgId, String role,
                         Integer dataScope, Integer tenantStatus, String tenantName, String orgName) {
        this.token = token;
        this.userId = userId;
        this.tenantId = tenantId;
        this.orgId = orgId;
        this.role = role;
        this.dataScope = dataScope;
        this.tenantStatus = tenantStatus;
        this.tenantName = tenantName;
        this.orgName = orgName;
    }

    public String getToken() {
        return token;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public Long getOrgId() {
        return orgId;
    }

    public String getRole() {
        return role;
    }

    public Integer getDataScope() {
        return dataScope;
    }

    public Integer getTenantStatus() {
        return tenantStatus;
    }

    public String getTenantName() {
        return tenantName;
    }

    public String getOrgName() {
        return orgName;
    }
}
