package com.xiaoa.work.model;

import java.time.LocalDateTime;

/**
 * 作品实体（原 com.xiaoa.ai.model.Work，随创作域归拢到 work 模块）。
 * 新增发布维度：publishStatus / caption / sourceAssetIds。
 */
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
    /** 生成维度状态：PENDING/SUCCESS/FAILED */
    private String status;
    private String failReason;
    /** 发布维度状态：NONE/DRAFT/PENDING_AUDIT/APPROVED/REJECTED/PUBLISHED */
    private String publishStatus;
    /** 配套文案（图文成套，生成成功后异步补齐，可手动修改） */
    private String caption;
    /** 引用素材快照，JSON 数组字符串 */
    private String sourceAssetIds;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    /** 最近一次审核结论（非表字段，详情接口组装） */
    private String auditStatus;
    /** 最近一次审核意见（非表字段，详情接口组装） */
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
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public String getAuditStatus() { return auditStatus; }
    public void setAuditStatus(String auditStatus) { this.auditStatus = auditStatus; }
    public String getAuditOpinion() { return auditOpinion; }
    public void setAuditOpinion(String auditOpinion) { this.auditOpinion = auditOpinion; }
}
