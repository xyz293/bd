package com.xiaoa.admin.controller;

import com.xiaoa.admin.model.DashboardOverview;
import com.xiaoa.admin.model.DashboardTrendPoint;
import com.xiaoa.admin.service.AdminDashboardService;
import com.xiaoa.common.api.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/dashboard")
public class AdminDashboardController {

    private final AdminDashboardService dashboardService;

    public AdminDashboardController(AdminDashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/overview")
    public Result<DashboardOverview> overview() {
        return Result.success(dashboardService.overview());
    }

    @GetMapping("/trend")
    public Result<List<DashboardTrendPoint>> trend() {
        return Result.success(dashboardService.trend());
    }
}
