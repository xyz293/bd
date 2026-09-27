package com.xiaoa.ai.model;

import java.time.LocalDateTime;

public class Work {

    private Long id;
    private Long tenantId;
    private Long userId;
    private String type;
    private Long mediaTaskId;
    private String platform;
    private Long styleId;
    private String styleName;
    private String userInput;
    private String refImageUrls;
    private Long promptTemplateId;
    private Integer promptTemplateVersion;
    private String contentUrl;
    private String copywriting;
    private String status;
    private String failReason;
    private String publishStatus;
    private String caption;
    private String sourceAssetIds;
    /** 关联对话会话：去配图溯源；有值时 CaptionService 跳过自动文案（用对话产出文案） */
    private Long chatSessionId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    /** 非表字段：最新审核意见，由服务层填充。 */
    private String auditOpinion;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public Long getMediaTaskId() { return mediaTaskId; }
    public void setMediaTaskId(Long mediaTaskId) { this.mediaTaskId = mediaTaskId; }
    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }
    public Long getStyleId() { return styleId; }
    public void setStyleId(Long styleId) { this.styleId = styleId; }
    public String getStyleName() { return styleName; }
    public void setStyleName(String styleName) { this.styleName = styleName; }
    public String getUserInput() { return userInput; }
    public void setUserInput(String userInput) { this.userInput = userInput; }
    public String getRefImageUrls() { return refImageUrls; }
    public void setRefImageUrls(String refImageUrls) { this.refImageUrls = refImageUrls; }
    public Long getPromptTemplateId() { return promptTemplateId; }
    public void setPromptTemplateId(Long promptTemplateId) { this.promptTemplateId = promptTemplateId; }
    public Integer getPromptTemplateVersion() { return promptTemplateVersion; }
    public void setPromptTemplateVersion(Integer promptTemplateVersion) { this.promptTemplateVersion = promptTemplateVersion; }
    public String getContentUrl() { return contentUrl; }
    public void setContentUrl(String contentUrl) { this.contentUrl = contentUrl; }
    public String getCopywriting() { return copywriting; }
    public void setCopywriting(String copywriting) { this.copywriting = copywriting; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getFailReason() { return failReason; }
    public void setFailReason(String failReason) { this.failReason = failReason; }
    public String getPublishStatus() { return publishStatus; }
    public void setPublishStatus(String publishStatus) { this.publishStatus = publishStatus; }
    public String getCaption() { return caption; }
    public void setCaption(String caption) { this.caption = caption; }
    public String getSourceAssetIds() { return sourceAssetIds; }
    public void setSourceAssetIds(String sourceAssetIds) { this.sourceAssetIds = sourceAssetIds; }
    public Long getChatSessionId() { return chatSessionId; }
    public void setChatSessionId(Long chatSessionId) { this.chatSessionId = chatSessionId; }
    public String getAuditOpinion() { return auditOpinion; }
    public void setAuditOpinion(String auditOpinion) { this.auditOpinion = auditOpinion; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
