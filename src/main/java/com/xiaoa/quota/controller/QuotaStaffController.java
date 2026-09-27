package com.xiaoa.quota.controller;

import com.xiaoa.common.auth.AuthContext;
import com.xiaoa.common.api.Result;
import com.xiaoa.quota.dto.AllocateStaffQuotaRequest;
import com.xiaoa.quota.dto.RecallStaffQuotaRequest;
import com.xiaoa.quota.service.QuotaService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * 店长员工额度管理：划拨（门店池→员工）与回收（员工→门店池）。
 * 双流水 + 幂等键，回收金额不能超过员工当前余额。
 */
@RestController
@RequestMapping("/api/quota/staff")
@Validated
public class QuotaStaffController {

    private final QuotaService quotaService;

    public QuotaStaffController(QuotaService quotaService) {
        this.quotaService = quotaService;
    }

    /** 店长向员工划拨额度（仅 OWNER） */
    @PostMapping("/allocate")
    public Result<Void> allocate(@Valid @RequestBody AllocateStaffQuotaRequest request) {
        quotaService.allocateToStaff(AuthContext.required(), request);
        return Result.success();
    }

    /** 店长回收员工未用额度（仅 OWNER） */
    @PostMapping("/recall")
    public Result<Void> recall(@Valid @RequestBody RecallStaffQuotaRequest request) {
        quotaService.recallFromStaff(AuthContext.required(), request);
        return Result.success();
    }
}
