package com.xiaoa.task.model;

import java.time.LocalDate;
import java.util.List;

public class AdminTaskBoardResponse {

    private final LocalDate date;
    private final List<AdminTaskBoardItem> tasks;

    public AdminTaskBoardResponse(LocalDate date, List<AdminTaskBoardItem> tasks) {
        this.date = date;
        this.tasks = tasks;
    }

    public LocalDate getDate() { return date; }
    public List<AdminTaskBoardItem> getTasks() { return tasks; }
}
