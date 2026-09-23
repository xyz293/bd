package com.xiaoa.task.model;

import java.time.LocalDateTime;

public class TaskModifyLog {

    private Long id;
    private Long tenantId;
    private Long taskId;
    private Long modifiedBy;
    private String changeDetail;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public Long getModifiedBy() { return modifiedBy; }
    public void setModifiedBy(Long modifiedBy) { this.modifiedBy = modifiedBy; }
    public String getChangeDetail() { return changeDetail; }
    public void setChangeDetail(String changeDetail) { this.changeDetail = changeDetail; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
