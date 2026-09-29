package com.xiaoa.ai.chat.dto;

import javax.validation.constraints.NotNull;

/**
 * 挂起的选项卡恢复载荷（Redis 持久化，方案 ⑤/⑥）：
 * 携带恢复执行所需的身份信息（超时兜底任务在无登录态的调度线程里重建 principal）。
 */
public class PendingOption {

    @NotNull
    private Long tenantId;
    @NotNull
    private Long userId;
    private Long orgId;
    private String role;
    private Integer dataScope;
    @NotNull
    private Long sessionId;
    /** 挂起截止时间（epoch 毫秒）：前端 30s，后端超此值 +60s 兜底放行 */
    private long deadline;

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getOrgId() { return orgId; }
    public void setOrgId(Long orgId) { this.orgId = orgId; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public Integer getDataScope() { return dataScope; }
    public void setDataScope(Integer dataScope) { this.dataScope = dataScope; }
    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }
    public long getDeadline() { return deadline; }
    public void setDeadline(long deadline) { this.deadline = deadline; }
}
