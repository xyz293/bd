package com.xiaoa.task.dto;

import com.xiaoa.task.model.TaskRecord;

import java.time.LocalDate;
import java.util.List;

public class AdminTaskReportResponse {

    private final Long taskId;
    private final LocalDate periodDate;
    private final long expected;
    private final long finished;
    private final double completionRate;
    private final List<TaskRecord> records;

    public AdminTaskReportResponse(Long taskId, LocalDate periodDate, long expected, long finished,
                                   List<TaskRecord> records) {
        this.taskId = taskId;
        this.periodDate = periodDate;
        this.expected = expected;
        this.finished = finished;
        this.completionRate = expected == 0 ? 0D : (double) finished / expected;
        this.records = records;
    }

    public Long getTaskId() { return taskId; }
    public LocalDate getPeriodDate() { return periodDate; }
    public long getExpected() { return expected; }
    public long getFinished() { return finished; }
    public double getCompletionRate() { return completionRate; }
    public List<TaskRecord> getRecords() { return records; }
}
