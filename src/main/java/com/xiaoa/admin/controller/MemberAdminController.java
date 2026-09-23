package com.xiaoa.admin.controller;

import com.xiaoa.admin.service.MemberAdminService;
import com.xiaoa.common.api.PageResult;
import com.xiaoa.common.api.Result;
import com.xiaoa.tenant.model.UserAccount;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/members")
public class MemberAdminController {

    private final MemberAdminService memberService;

    public MemberAdminController(MemberAdminService memberService) {
        this.memberService = memberService;
    }

    @GetMapping
    public Result<PageResult<UserAccount>> list(@RequestParam(required = false) Long orgId,
                                                @RequestParam(defaultValue = "1") int pageNo,
                                                @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(memberService.list(orgId, pageNo, pageSize));
    }
}
