package com.xiaoa.ai.chat.model;

import java.time.LocalDateTime;

/**
 * 对话消息。role：USER 员工输入 / AI 模型回复。
 * AI 消息的 content 为 JSON 字符串：
 * {"action":"ASK","question":"..."} 或 {"action":"GENERATE","versions":["v1","v2","v3"],"revisedFrom":2}
 */
public class ChatMessage {

    public static final String ROLE_USER = "USER";
    public static final String ROLE_AI = "AI";

    private Long id;
    private Long tenantId;
    private Long sessionId;
    private Long userId;
    private String role;
    private String content;
    private Integer tokenCount;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Integer getTokenCount() { return tokenCount; }
    public void setTokenCount(Integer tokenCount) { this.tokenCount = tokenCount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
