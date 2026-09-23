package com.xiaoa.task.dto;

import com.xiaoa.task.model.TaskRecord;

import java.time.LocalDate;
import java.util.List;

public class TaskBoardResponse {

    private final Long storeId;
    private final LocalDate periodDate;
    private final long expected;
    private final long finished;
    private final double completionRate;
    private final List<TaskRecord> unfinished;

    public TaskBoardResponse(Long storeId, LocalDate periodDate, long expected, long finished,
                             List<TaskRecord> unfinished) {
        this.storeId = storeId;
        this.periodDate = periodDate;
        this.expected = expected;
        this.finished = finished;
        this.completionRate = expected == 0 ? 0D : (double) finished / expected;
        this.unfinished = unfinished;
    }

    public Long getStoreId() { return storeId; }
    public LocalDate getPeriodDate() { return periodDate; }
    public long getExpected() { return expected; }
    public long getFinished() { return finished; }
    public double getCompletionRate() { return completionRate; }
    public List<TaskRecord> getUnfinished() { return unfinished; }
}
