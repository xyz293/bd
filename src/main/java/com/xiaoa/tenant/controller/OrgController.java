package com.xiaoa.tenant.controller;

import com.xiaoa.common.api.Result;
import com.xiaoa.tenant.dto.CreateOrgRequest;
import com.xiaoa.tenant.dto.RenameOrgRequest;
import com.xiaoa.tenant.model.Org;
import com.xiaoa.tenant.model.OrgTreeNode;
import com.xiaoa.tenant.service.OrgService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/orgs")
@Validated
public class OrgController {

    private final OrgService orgService;

    public OrgController(OrgService orgService) {
        this.orgService = orgService;
    }

    @PostMapping
    public Result<Org> create(@Valid @RequestBody CreateOrgRequest request) {
        return Result.success(orgService.create(request));
    }

    @GetMapping("/tree")
    public Result<List<OrgTreeNode>> tree() {
        return Result.success(orgService.tree());
    }

    @PatchMapping("/{orgId}/name")
    public Result<Void> rename(@PathVariable Long orgId, @Valid @RequestBody RenameOrgRequest request) {
        orgService.rename(orgId, request.getName());
        return Result.success();
    }
}
