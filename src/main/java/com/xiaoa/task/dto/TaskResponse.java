package com.xiaoa.task.dto;

import com.xiaoa.task.model.Task;
import com.xiaoa.task.model.TaskRecord;

import java.time.LocalDate;

public class TaskResponse {

    private final Long id;
    private final String title;
    private final Integer formType;
    private final Long contentPackageId;
    private final String platform;
    private final Integer frequency;
    private final Integer targetScope;
    private final Integer judgeType;
    private final Long sourceTaskId;
    private final Integer createdLevel;
    private final Integer status;
    private final LocalDate periodDate;
    private final Integer recordStatus;
    private final Long publishRecordId;

    public TaskResponse(Task task, TaskRecord record, LocalDate periodDate) {
        this.id = task.getId();
        this.title = task.getTitle();
        this.formType = task.getFormType();
        this.contentPackageId = task.getContentPackageId();
        this.platform = task.getPlatform();
        this.frequency = task.getFrequency();
        this.targetScope = task.getTargetScope();
        this.judgeType = task.getJudgeType();
        this.sourceTaskId = task.getSourceTaskId();
        this.createdLevel = task.getCreatedLevel();
        this.status = task.getStatus();
        this.periodDate = periodDate;
        this.recordStatus = record == null ? 0 : record.getStatus();
        this.publishRecordId = record == null ? null : record.getPublishRecordId();
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public Integer getFormType() { return formType; }
    public Long getContentPackageId() { return contentPackageId; }
    public String getPlatform() { return platform; }
    public Integer getFrequency() { return frequency; }
    public Integer getTargetScope() { return targetScope; }
    public Integer getJudgeType() { return judgeType; }
    public Long getSourceTaskId() { return sourceTaskId; }
    public Integer getCreatedLevel() { return createdLevel; }
    public Integer getStatus() { return status; }
    public LocalDate getPeriodDate() { return periodDate; }
    public Integer getRecordStatus() { return recordStatus; }
    public Long getPublishRecordId() { return publishRecordId; }
}
