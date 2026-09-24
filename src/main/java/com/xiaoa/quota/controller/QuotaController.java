package com.xiaoa.quota.controller;

import com.xiaoa.common.api.PageResult;
import com.xiaoa.common.api.Result;
import com.xiaoa.quota.dto.AllocateQuotaRequest;
import com.xiaoa.quota.dto.CreditQuotaRequest;
import com.xiaoa.quota.dto.QuotaFlowQuery;
import com.xiaoa.quota.model.QuotaAccount;
import com.xiaoa.quota.model.QuotaFlow;
import com.xiaoa.quota.service.QuotaService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/admin/quota")
@Validated
public class QuotaController {

    private final QuotaService quotaService;

    public QuotaController(QuotaService quotaService) {
        this.quotaService = quotaService;
    }

    @PostMapping("/credit")
    public Result<QuotaAccount> credit(@Valid @RequestBody CreditQuotaRequest request) {
        return Result.success(quotaService.credit(request));
    }

    @PostMapping("/allocate")
    public Result<Void> allocate(@Valid @RequestBody AllocateQuotaRequest request) {
        quotaService.allocate(request);
        return Result.success();
    }

    @GetMapping("/store/{storeId}")
    public Result<QuotaAccount> store(@PathVariable Long storeId) {
        return Result.success(quotaService.getStore(storeId));
    }

    @GetMapping("/account/{accountId}/flows")
    public Result<PageResult<QuotaFlow>> flows(@PathVariable Long accountId,
                                               @Valid @ModelAttribute QuotaFlowQuery query) {
        return Result.success(quotaService.flows(accountId, query));
    }
}
