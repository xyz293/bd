package com.xiaoa.asset.dto;

import com.fasterxml.jackson.databind.JsonNode;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class ContentPackageRequest {

    @NotBlank(message = "内容包名称不能为空")
    @Size(max = 128, message = "内容包名称长度不能超过128")
    private String name;

    /** 营销日，早于今天拒绝 */
    @NotNull(message = "营销日不能为空")
    private LocalDate calendarDate;

    /** 下发时刻 */
    @NotNull(message = "下发时间不能为空")
    private LocalDateTime publishAt;

    @Size(max = 2000, message = "推广方向长度不能超过2000")
    private String copyDirection;

    /** 任务模板 JSON：actionType/platform/frequency/judgeType/endTime 必填 */
    @NotNull(message = "任务模板不能为空")
    private JsonNode taskTemplate;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public LocalDate getCalendarDate() { return calendarDate; }
    public void setCalendarDate(LocalDate calendarDate) { this.calendarDate = calendarDate; }
    public LocalDateTime getPublishAt() { return publishAt; }
    public void setPublishAt(LocalDateTime publishAt) { this.publishAt = publishAt; }
    public String getCopyDirection() { return copyDirection; }
    public void setCopyDirection(String copyDirection) { this.copyDirection = copyDirection; }
    public JsonNode getTaskTemplate() { return taskTemplate; }
    public void setTaskTemplate(JsonNode taskTemplate) { this.taskTemplate = taskTemplate; }
}
