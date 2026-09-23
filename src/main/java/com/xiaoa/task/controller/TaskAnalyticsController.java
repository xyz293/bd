package com.xiaoa.task.controller;

import com.xiaoa.common.api.Result;
import com.xiaoa.task.model.AdminTaskBoardResponse;
import com.xiaoa.task.model.AdminTaskStoreItem;
import com.xiaoa.task.model.TaskBadge;
import com.xiaoa.task.model.TaskRankingItem;
import com.xiaoa.task.model.TaskRecordDetail;
import com.xiaoa.task.service.TaskAnalyticsService;
import com.xiaoa.task.service.TaskAchievementService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/task")
public class TaskAnalyticsController {

    private final TaskAnalyticsService adminTaskService;
    private final TaskAchievementService achievementService;

    public TaskAnalyticsController(TaskAnalyticsService adminTaskService, TaskAchievementService achievementService) {
        this.adminTaskService = adminTaskService;
        this.achievementService = achievementService;
    }

    @GetMapping("/board")
    public Result<AdminTaskBoardResponse> board(@RequestParam(required = false)
                                                 @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                                 LocalDate date) {
        return Result.success(adminTaskService.board(date));
    }

    @GetMapping("/board/stores")
    public Result<List<AdminTaskStoreItem>> stores(@RequestParam Long taskId,
                                                    @RequestParam(required = false)
                                                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                                    LocalDate date) {
        return Result.success(adminTaskService.stores(taskId, date));
    }

    @GetMapping("/board/records")
    public Result<List<TaskRecordDetail>> records(@RequestParam Long taskId, @RequestParam Long storeId,
                                                   @RequestParam(required = false)
                                                   @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                                   LocalDate date) {
        return Result.success(adminTaskService.recordDetails(taskId, storeId, date));
    }

    @GetMapping("/ranking")
    public Result<List<TaskRankingItem>> ranking(@RequestParam(required = false) String scope,
                                                 @RequestParam(required = false) String period,
                                                 @RequestParam(required = false) Long storeId,
                                                 @RequestParam(required = false) Integer limit) {
        return Result.success(achievementService.ranking(scope, period, storeId, limit));
    }

    @GetMapping("/badges")
    public Result<List<TaskBadge>> badges(@RequestParam(required = false) Long userId,
                                          @RequestParam(required = false)
                                          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                          LocalDate date) {
        return Result.success(achievementService.badges(userId, date));
    }
}
