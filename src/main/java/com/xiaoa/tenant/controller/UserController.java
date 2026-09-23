package com.xiaoa.tenant.controller;

import com.xiaoa.common.api.Result;
import com.xiaoa.tenant.dto.UpdateRoleRequest;
import com.xiaoa.tenant.service.AccountService;
import com.xiaoa.tenant.service.RoleService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/users")
@Validated
public class UserController {

    private final AccountService accountService;
    private final RoleService roleService;

    public UserController(AccountService accountService, RoleService roleService) {
        this.accountService = accountService;
        this.roleService = roleService;
    }

    @DeleteMapping("/roles/{roleId}")
    public Result<Void> removeFromStore(@PathVariable Long roleId) {
        accountService.remove(roleId);
        return Result.success();
    }

    @PatchMapping("/{userId}/disable")
    public Result<Void> disable(@PathVariable Long userId) {
        accountService.disable(userId);
        return Result.success();
    }

    @PutMapping("/{userId}/roles")
    public Result<Void> grantRole(@PathVariable Long userId, @Valid @RequestBody UpdateRoleRequest request) {
        roleService.grant(userId, request);
        return Result.success();
    }

    @PatchMapping("/roles/{roleId}")
    public Result<Void> updateRole(@PathVariable Long roleId, @Valid @RequestBody UpdateRoleRequest request) {
        accountService.updateRole(roleId, request.getRole(), request.getDataScope());
        return Result.success();
    }
}
