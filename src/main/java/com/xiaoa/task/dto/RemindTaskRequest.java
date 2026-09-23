package com.xiaoa.task.dto;

import javax.validation.constraints.NotNull;
import java.time.LocalDate;

public class RemindTaskRequest {

    @NotNull(message = "任务不能为空")
    private Long taskId;
    private LocalDate periodDate;

    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public LocalDate getPeriodDate() { return periodDate; }
    public void setPeriodDate(LocalDate periodDate) { this.periodDate = periodDate; }
}
