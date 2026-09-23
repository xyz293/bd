package com.xiaoa.task.dto;

import javax.validation.constraints.NotBlank;
import java.time.LocalDateTime;

public class UpdateTaskRequest {

    @NotBlank(message = "任务标题不能为空")
    private String title;
    private Long contentPackageId;
    private String platform;
    private LocalDateTime startAt;
    private LocalDateTime endAt;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Long getContentPackageId() { return contentPackageId; }
    public void setContentPackageId(Long contentPackageId) { this.contentPackageId = contentPackageId; }
    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }
    public LocalDateTime getStartAt() { return startAt; }
    public void setStartAt(LocalDateTime startAt) { this.startAt = startAt; }
    public LocalDateTime getEndAt() { return endAt; }
    public void setEndAt(LocalDateTime endAt) { this.endAt = endAt; }
}
