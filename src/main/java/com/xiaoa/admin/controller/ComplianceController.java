package com.xiaoa.admin.controller;

import com.xiaoa.admin.dto.UpdateAuditConfigRequest;
import com.xiaoa.admin.dto.UpsertComplianceWordRequest;
import com.xiaoa.admin.model.AuditConfig;
import com.xiaoa.admin.model.ComplianceWord;
import com.xiaoa.admin.service.ComplianceService;
import com.xiaoa.common.api.Result;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/admin/compliance")
@Validated
public class ComplianceController {

    private final ComplianceService complianceService;

    public ComplianceController(ComplianceService complianceService) {
        this.complianceService = complianceService;
    }

    @GetMapping("/words")
    public Result<List<ComplianceWord>> words() {
        return Result.success(complianceService.words());
    }

    @PutMapping("/words")
    public Result<ComplianceWord> saveWord(@Valid @RequestBody UpsertComplianceWordRequest request) {
        return Result.success(complianceService.saveWord(request));
    }

    @DeleteMapping("/words/{id}")
    public Result<Void> disableWord(@PathVariable Long id) {
        complianceService.disableWord(id);
        return Result.success();
    }

    @GetMapping("/audit-config/{orgId}")
    public Result<AuditConfig> getAuditConfig(@PathVariable Long orgId) {
        return Result.success(complianceService.getAuditConfig(orgId));
    }

    @PutMapping("/audit-config/{orgId}")
    public Result<AuditConfig> updateAuditConfig(@PathVariable Long orgId,
                                                 @Valid @RequestBody UpdateAuditConfigRequest request) {
        return Result.success(complianceService.updateAuditConfig(orgId, request));
    }
}
