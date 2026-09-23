package com.xiaoa.task.controller;

import com.xiaoa.common.api.Result;
import com.xiaoa.task.dto.CreateTaskRequest;
import com.xiaoa.task.dto.RemindTaskRequest;
import com.xiaoa.task.dto.TaskBoardResponse;
import com.xiaoa.task.dto.TaskResponse;
import com.xiaoa.task.dto.UpdateTaskRequest;
import com.xiaoa.task.dto.UpdateTaskStatusRequest;
import com.xiaoa.task.model.Task;
import com.xiaoa.task.model.TaskModifyLog;
import com.xiaoa.task.service.TaskBoardService;
import com.xiaoa.task.service.TaskReminderService;
import com.xiaoa.task.service.TaskService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/task")
@Validated
public class TaskController {

    private final TaskService taskService;
    private final TaskBoardService boardService;
    private final TaskReminderService reminderService;

    public TaskController(TaskService taskService, TaskBoardService boardService,
                          TaskReminderService reminderService) {
        this.taskService = taskService;
        this.boardService = boardService;
        this.reminderService = reminderService;
    }

    @PostMapping
    public Result<Task> create(@Valid @RequestBody CreateTaskRequest request) {
        return Result.success(taskService.create(request));
    }

    @PutMapping("/{id}")
    public Result<Task> update(@PathVariable Long id, @Valid @RequestBody UpdateTaskRequest request) {
        return Result.success(taskService.update(id, request));
    }

    @PatchMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody UpdateTaskStatusRequest request) {
        taskService.updateStatus(id, request.getStatus());
        return Result.success();
    }

    @GetMapping("/{id}")
    public Result<Task> get(@PathVariable Long id) {
        return Result.success(taskService.get(id));
    }

    @GetMapping("/my")
    public Result<List<TaskResponse>> myTasks() {
        return Result.success(taskService.myTasks());
    }

    @GetMapping("/{id}/modify-logs")
    public Result<List<TaskModifyLog>> modifyLogs(@PathVariable Long id) {
        return Result.success(taskService.modifyLogs(id));
    }

    @PostMapping("/remind")
    public Result<Integer> remind(@Valid @RequestBody RemindTaskRequest request) {
        return Result.success(reminderService.remind(request));
    }

    @GetMapping("/store-board")
    public Result<TaskBoardResponse> storeBoard(@RequestParam Long storeId,
                                                @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                                LocalDate periodDate) {
        return Result.success(boardService.storeBoard(storeId, periodDate));
    }
}
