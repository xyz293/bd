package com.xiaoa.tenant.controller;

import com.xiaoa.common.api.Result;
import com.xiaoa.tenant.dto.OpenTenantRequest;
import com.xiaoa.tenant.dto.OpenTenantResponse;
import com.xiaoa.tenant.model.Tenant;
import com.xiaoa.tenant.service.TenantService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/tenants")
@Validated
public class TenantController {

    private final TenantService tenantService;

    public TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @PostMapping("/open")
    public Result<OpenTenantResponse> open(@Valid @RequestBody OpenTenantRequest request) {
        return Result.success(tenantService.open(request));
    }

    @GetMapping("/{tenantId}")
    public Result<Tenant> get(@PathVariable Long tenantId) {
        return Result.success(tenantService.get(tenantId));
    }
}
