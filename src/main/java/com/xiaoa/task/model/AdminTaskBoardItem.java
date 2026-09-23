package com.xiaoa.task.model;

public class AdminTaskBoardItem {

    private final Long taskId;
    private final String title;
    private final Integer status;
    private final Integer targetScope;
    private final java.time.LocalDate periodDate;
    private final long expected;
    private final long finished;
    private final double completionRate;

    public AdminTaskBoardItem(Long taskId, String title, Integer status, Integer targetScope,
                              java.time.LocalDate periodDate, long expected, long finished) {
        this.taskId = taskId;
        this.title = title;
        this.status = status;
        this.targetScope = targetScope;
        this.periodDate = periodDate;
        this.expected = expected;
        this.finished = finished;
        this.completionRate = expected == 0 ? 0D : (double) finished / expected;
    }

    public Long getTaskId() { return taskId; }
    public String getTitle() { return title; }
    public Integer getStatus() { return status; }
    public Integer getTargetScope() { return targetScope; }
    public java.time.LocalDate getPeriodDate() { return periodDate; }
    public long getExpected() { return expected; }
    public long getFinished() { return finished; }
    public double getCompletionRate() { return completionRate; }
}
