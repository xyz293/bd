package com.xiaoa.admin.controller;

import com.xiaoa.admin.dto.RejectWorkRequest;
import com.xiaoa.admin.service.AuditService;
import com.xiaoa.ai.model.Work;
import com.xiaoa.common.api.Result;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/admin/audit")
@Validated
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping("/works")
    public Result<List<Work>> pending() {
        return Result.success(auditService.pending());
    }

    @PostMapping("/works/{id}/approve")
    public Result<Void> approve(@PathVariable Long id) {
        auditService.approve(id);
        return Result.success();
    }

    @PostMapping("/works/{id}/reject")
    public Result<Void> reject(@PathVariable Long id, @Valid @RequestBody RejectWorkRequest request) {
        auditService.reject(id, request.getOpinion());
        return Result.success();
    }
}
