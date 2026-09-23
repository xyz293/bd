package com.xiaoa.tenant.controller;

import com.xiaoa.common.api.Result;
import com.xiaoa.tenant.dto.CreateInviteRequest;
import com.xiaoa.tenant.dto.InviteResponse;
import com.xiaoa.tenant.model.InviteCode;
import com.xiaoa.tenant.service.InviteService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/invites")
@Validated
public class InviteController {

    private final InviteService inviteService;

    public InviteController(InviteService inviteService) {
        this.inviteService = inviteService;
    }

    @PostMapping
    public Result<InviteResponse> create(@Valid @RequestBody CreateInviteRequest request) {
        return Result.success(inviteService.create(request));
    }

    @GetMapping("/{code}")
    public Result<InviteResponse> validate(@PathVariable String code) {
        InviteCode invite = inviteService.validate(code);
        return Result.success(new InviteResponse(invite.getId(), invite.getStoreId(), invite.getCode(),
                invite.getRole(), invite.getExpireAt()));
    }
}
