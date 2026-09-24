package com.xiaoa.quota.controller;

import com.xiaoa.common.api.Result;
import com.xiaoa.quota.model.QuotaSummary;
import com.xiaoa.quota.service.QuotaService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/quota")
public class QuotaMyController {

    private final QuotaService quotaService;

    public QuotaMyController(QuotaService quotaService) {
        this.quotaService = quotaService;
    }

    @GetMapping("/my")
    public Result<QuotaSummary> my() {
        return Result.success(quotaService.my());
    }
}
