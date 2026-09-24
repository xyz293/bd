package com.xiaoa.asset.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 内容包：营销日配置任务模板，到点由定时任务下发为任务。
 * status：1 ACTIVE(待下发) / 2 DISPATCHED(已下发，即方案中的 EXPIRED) / 3 CANCELED(已撤销)。
 * 下发策略：先条件更新抢占状态、再建任务——宁可漏发（可补）、不可重发（难删）。
 */
public class ContentPackage {

    public static final int STATUS_ACTIVE = 1;
    public static final int STATUS_DISPATCHED = 2;
    public static final int STATUS_CANCELED = 3;

    private Long id;
    private Long tenantId;
    private String name;
    private LocalDate calendarDate;
    private LocalDateTime publishAt;
    private String copyDirection;
    /** 任务模板 JSON 字符串 */
    private String taskTemplate;
    private Integer status;
    private Long sourceTaskId;
    private String lastError;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public LocalDate getCalendarDate() { return calendarDate; }
    public void setCalendarDate(LocalDate calendarDate) { this.calendarDate = calendarDate; }
    public LocalDateTime getPublishAt() { return publishAt; }
    public void setPublishAt(LocalDateTime publishAt) { this.publishAt = publishAt; }
    public String getCopyDirection() { return copyDirection; }
    public void setCopyDirection(String copyDirection) { this.copyDirection = copyDirection; }
    public String getTaskTemplate() { return taskTemplate; }
    public void setTaskTemplate(String taskTemplate) { this.taskTemplate = taskTemplate; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public Long getSourceTaskId() { return sourceTaskId; }
    public void setSourceTaskId(Long sourceTaskId) { this.sourceTaskId = sourceTaskId; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
