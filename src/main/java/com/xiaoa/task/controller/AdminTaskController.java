package com.xiaoa.task.controller;

import com.xiaoa.common.api.Result;
import com.xiaoa.task.dto.AdminTaskReportResponse;
import com.xiaoa.task.service.AdminTaskService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/admin/task")
public class AdminTaskController {

    private final AdminTaskService taskService;

    public AdminTaskController(AdminTaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping("/{taskId}/report")
    public Result<AdminTaskReportResponse> report(@PathVariable Long taskId,
                                                   @RequestParam(required = false) Long storeId,
                                                   @RequestParam(required = false)
                                                   @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                                   LocalDate periodDate) {
        return Result.success(taskService.report(taskId, storeId, periodDate));
    }
}
