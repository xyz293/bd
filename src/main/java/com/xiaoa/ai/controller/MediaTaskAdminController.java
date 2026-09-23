package com.xiaoa.ai.controller;

import com.xiaoa.ai.model.MediaTask;
import com.xiaoa.ai.service.AiGatewayService;
import com.xiaoa.common.api.Result;
import com.xiaoa.common.auth.AdminPermissionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/media-task")
public class MediaTaskAdminController {

    private final AiGatewayService aiGatewayService;
    private final AdminPermissionService permissionService;

    public MediaTaskAdminController(AiGatewayService aiGatewayService, AdminPermissionService permissionService) {
        this.aiGatewayService = aiGatewayService;
        this.permissionService = permissionService;
    }

    @GetMapping("/list")
    public Result<List<MediaTask>> list(@RequestParam(defaultValue = "50") int limit) {
        return Result.success(aiGatewayService.adminTasks(permissionService.requiredRead().getTenantId(), limit));
    }
}
