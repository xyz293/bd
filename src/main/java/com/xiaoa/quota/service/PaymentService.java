package com.xiaoa.quota.service;

import com.xiaoa.common.auth.AuthContext;
import com.xiaoa.common.auth.AuthPrincipal;
import com.xiaoa.common.exception.BusinessException;
import com.xiaoa.common.exception.ErrorCode;
import com.xiaoa.quota.dto.ConfirmPaymentRequest;
import com.xiaoa.quota.dto.RegisterPaymentRequest;
import com.xiaoa.quota.mapper.PaymentOrderMapper;
import com.xiaoa.quota.model.PaymentOrder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class PaymentService {

    private final PaymentOrderMapper paymentOrderMapper;
    private final QuotaService quotaService;

    public PaymentService(PaymentOrderMapper paymentOrderMapper, QuotaService quotaService) {
        this.paymentOrderMapper = paymentOrderMapper;
        this.quotaService = quotaService;
    }

    @Transactional
    public PaymentOrder register(RegisterPaymentRequest request) {
        requireRole("PLATFORM_FINANCE");
        String orderNo = request.getOrderNo();
        if (orderNo == null || orderNo.trim().isEmpty()) {
            orderNo = "PAY-" + UUID.randomUUID().toString().replace("-", "");
        }
        PaymentOrder existing = paymentOrderMapper.findByOrderNo(orderNo);
        if (existing != null) {
            return existing;
        }
        PaymentOrder order = new PaymentOrder();
        order.setTenantId(request.getTenantId());
        order.setOrderNo(orderNo);
        order.setAmount(request.getAmount());
        order.setChannel(request.getChannel());
        order.setVoucherUrl(request.getVoucherUrl());
        order.setInvoiceNo(request.getInvoiceNo());
        paymentOrderMapper.insert(order);
        return paymentOrderMapper.findById(order.getId());
    }

    @Transactional
    public PaymentOrder confirm(Long orderId, ConfirmPaymentRequest request) {
        AuthPrincipal principal = requireRole("PLATFORM_FINANCE");
        PaymentOrder order = requireOrder(orderId);
        if ("SETTLED".equals(order.getStatus())) {
            return order;
        }
        if (!"PENDING".equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "当前收款单状态不可确认");
        }
        String key = "pay:" + order.getId();
        quotaService.creditTenantPool(order.getTenantId(), order.getAmount(), key,
                "收款单确认入账:" + order.getOrderNo());
        int rows = paymentOrderMapper.settle(order.getId(), principal.getUserId(),
                request == null ? null : request.getInvoiceNo());
        if (rows == 0) {
            PaymentOrder current = requireOrder(orderId);
            if (!"SETTLED".equals(current.getStatus())) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "收款单确认状态更新失败");
            }
            return current;
        }
        return paymentOrderMapper.findById(orderId);
    }

    @Transactional
    public PaymentOrder cancel(Long orderId) {
        requireRole("PLATFORM_FINANCE");
        PaymentOrder order = requireOrder(orderId);
        if ("CANCELED".equals(order.getStatus())) {
            return order;
        }
        if (!"PENDING".equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "当前收款单状态不可撤销");
        }
        paymentOrderMapper.cancel(orderId);
        return paymentOrderMapper.findById(orderId);
    }

    private PaymentOrder requireOrder(Long orderId) {
        PaymentOrder order = paymentOrderMapper.findById(orderId);
        if (order == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "收款单不存在");
        }
        return order;
    }

    private AuthPrincipal requireRole(String... roles) {
        AuthPrincipal principal = AuthContext.required();
        for (String role : roles) {
            if (role.equals(principal.getRole())) {
                return principal;
            }
        }
        throw new BusinessException(ErrorCode.FORBIDDEN, "当前角色没有收款单操作权限");
    }
}
