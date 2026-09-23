package com.xiaoa.task.model;

import java.time.LocalDateTime;

public class Task {

    private Long id;
    private Long tenantId;
    private String title;
    private Integer formType;
    private Long contentPackageId;
    private String platform;
    private Integer frequency;
    private Integer targetScope;
    private String targetIds;
    private Integer judgeType;
    private Long sourceTaskId;
    private Long createdBy;
    private Integer createdLevel;
    private Integer status;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Integer getFormType() { return formType; }
    public void setFormType(Integer formType) { this.formType = formType; }
    public Long getContentPackageId() { return contentPackageId; }
    public void setContentPackageId(Long contentPackageId) { this.contentPackageId = contentPackageId; }
    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }
    public Integer getFrequency() { return frequency; }
    public void setFrequency(Integer frequency) { this.frequency = frequency; }
    public Integer getTargetScope() { return targetScope; }
    public void setTargetScope(Integer targetScope) { this.targetScope = targetScope; }
    public String getTargetIds() { return targetIds; }
    public void setTargetIds(String targetIds) { this.targetIds = targetIds; }
    public Integer getJudgeType() { return judgeType; }
    public void setJudgeType(Integer judgeType) { this.judgeType = judgeType; }
    public Long getSourceTaskId() { return sourceTaskId; }
    public void setSourceTaskId(Long sourceTaskId) { this.sourceTaskId = sourceTaskId; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public Integer getCreatedLevel() { return createdLevel; }
    public void setCreatedLevel(Integer createdLevel) { this.createdLevel = createdLevel; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public LocalDateTime getStartAt() { return startAt; }
    public void setStartAt(LocalDateTime startAt) { this.startAt = startAt; }
    public LocalDateTime getEndAt() { return endAt; }
    public void setEndAt(LocalDateTime endAt) { this.endAt = endAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
