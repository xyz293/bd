package com.xiaoa.task.dto;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;

public class CreateTaskRequest {

    @NotBlank(message = "任务标题不能为空")
    private String title;
    @NotNull @Min(1) @Max(2)
    private Integer formType;
    private Long contentPackageId;
    private String platform;
    @NotNull @Min(1) @Max(3)
    private Integer frequency;
    @NotNull @Min(1) @Max(4)
    private Integer targetScope;
    private List<Long> targetIds;
    @NotNull @Min(1) @Max(2)
    private Integer judgeType = 1;
    private LocalDateTime startAt;
    private LocalDateTime endAt;

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
    public List<Long> getTargetIds() { return targetIds; }
    public void setTargetIds(List<Long> targetIds) { this.targetIds = targetIds; }
    public Integer getJudgeType() { return judgeType; }
    public void setJudgeType(Integer judgeType) { this.judgeType = judgeType; }
    public LocalDateTime getStartAt() { return startAt; }
    public void setStartAt(LocalDateTime startAt) { this.startAt = startAt; }
    public LocalDateTime getEndAt() { return endAt; }
    public void setEndAt(LocalDateTime endAt) { this.endAt = endAt; }
}
