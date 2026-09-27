package com.xiaoa.ai.chat.model;

import java.time.LocalDateTime;

/**
 * 对话会话：引导式聊天创作。
 * context 存要素收集状态 JSON（product/sellingPoint/audience/rounds 等）；
 * status：ACTIVE 可对话 / CLOSED 已关闭（7 天不活跃或手动关闭）。
 */
public class ChatSession {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_CLOSED = "CLOSED";

    private Long id;
    private Long tenantId;
    private Long userId;
    private String title;
    private String scene;
    private String status;
    /** 要素收集状态 JSON 字符串 */
    private String context;
    private Integer reviseCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getScene() { return scene; }
    public void setScene(String scene) { this.scene = scene; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getContext() { return context; }
    public void setContext(String context) { this.context = context; }
    public Integer getReviseCount() { return reviseCount; }
    public void setReviseCount(Integer reviseCount) { this.reviseCount = reviseCount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
