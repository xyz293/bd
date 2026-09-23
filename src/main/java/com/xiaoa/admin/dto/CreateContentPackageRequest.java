package com.xiaoa.admin.dto;

import javax.validation.constraints.NotBlank;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class CreateContentPackageRequest {

    @NotBlank
    private String name;
    private LocalDate calendarDate;
    private LocalDateTime publishAt;
    private String copyDirection;
    private String taskTemplate;

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
}
