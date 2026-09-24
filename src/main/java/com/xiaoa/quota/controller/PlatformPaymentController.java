package com.xiaoa.quota.controller;

import com.xiaoa.common.api.Result;
import com.xiaoa.quota.dto.ConfirmPaymentRequest;
import com.xiaoa.quota.dto.RegisterPaymentRequest;
import com.xiaoa.quota.model.PaymentOrder;
import com.xiaoa.quota.service.PaymentService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@RestController
@RequestMapping("/platform/payment")
@Validated
public class PlatformPaymentController {

    private final PaymentService paymentService;

    public PlatformPaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/register")
    public Result<PaymentOrder> register(@Valid @RequestBody RegisterPaymentRequest request) {
        return Result.success(paymentService.register(request));
    }

    @PostMapping("/{id}/confirm")
    public Result<PaymentOrder> confirm(@PathVariable Long id,
                                        @RequestBody(required = false) ConfirmPaymentRequest request) {
        return Result.success(paymentService.confirm(id, request));
    }

    @PostMapping("/{id}/cancel")
    public Result<PaymentOrder> cancel(@PathVariable Long id) {
        return Result.success(paymentService.cancel(id));
    }
}
