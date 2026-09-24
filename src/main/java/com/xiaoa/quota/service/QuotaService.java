package com.xiaoa.quota.service;

import com.xiaoa.common.api.PageResult;
import com.xiaoa.common.auth.AdminPermissionService;
import com.xiaoa.common.auth.AuthPrincipal;
import com.xiaoa.common.exception.BusinessException;
import com.xiaoa.common.exception.ErrorCode;
import com.xiaoa.quota.dto.AllocateQuotaRequest;
import com.xiaoa.quota.dto.CreditQuotaRequest;
import com.xiaoa.quota.dto.QuotaFlowQuery;
import com.xiaoa.quota.mapper.QuotaAccountMapper;
import com.xiaoa.quota.mapper.QuotaFlowMapper;
import com.xiaoa.quota.model.QuotaAccount;
import com.xiaoa.quota.model.QuotaBizType;
import com.xiaoa.quota.model.QuotaFlow;
import com.xiaoa.quota.model.QuotaSummary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
public class QuotaService {

    private static final int MAX_RETRY = 3;
    private static final int MAX_PAGE_SIZE = 200;

    private final QuotaAccountMapper accountMapper;
    private final QuotaFlowMapper flowMapper;
    private final AdminPermissionService permissionService;

    public QuotaService(QuotaAccountMapper accountMapper, QuotaFlowMapper flowMapper,
                        AdminPermissionService permissionService) {
        this.accountMapper = accountMapper;
        this.flowMapper = flowMapper;
        this.permissionService = permissionService;
    }

    @Transactional
    public QuotaAccount ensureStoreAccount(Long storeId) {
        Long tenantId = permissionService.required().getTenantId();
        return ensureStoreAccount(tenantId, storeId);
    }

    @Transactional
    public QuotaAccount ensureStoreAccount(Long tenantId, Long storeId) {
        if (tenantId == null || storeId == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "门店信息不能为空");
        }
        QuotaAccount account = accountMapper.findByOwner(tenantId, "STORE", storeId);
        if (account != null) {
            return account;
        }
        QuotaAccount created = new QuotaAccount();
        created.setTenantId(tenantId);
        created.setLevel("STORE");
        created.setOwnerId(storeId);
        created.setBalance(0L);
        created.setVersion(0);
        accountMapper.ensure(created);
        account = accountMapper.findByOwner(tenantId, "STORE", storeId);
        if (account == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "额度账户创建失败");
        }
        return account;
    }

    @Transactional
    public QuotaAccount ensureTenantPoolAccount(Long tenantId) {
        if (tenantId == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "租户不能为空");
        }
        QuotaAccount account = accountMapper.findByOwner(tenantId, "TENANT", tenantId);
        if (account != null) {
            return account;
        }
        QuotaAccount created = new QuotaAccount();
        created.setTenantId(tenantId);
        created.setLevel("TENANT");
        created.setOwnerId(tenantId);
        created.setBalance(0L);
        created.setVersion(0);
        accountMapper.ensure(created);
        account = accountMapper.findByOwner(tenantId, "TENANT", tenantId);
        if (account == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "租户额度账户创建失败");
        }
        return account;
    }

    @Transactional
    public QuotaAccount credit(CreditQuotaRequest request) {
        permissionService.requireHeadquarters();
        Long tenantId = permissionService.required().getTenantId();
        QuotaAccount account = accountMapper.findById(tenantId, request.getAccountId());
        if (account == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "额度账户不存在");
        }
        return credit(tenantId, account, request.getAmount(), requiredKey(request.getBizId(), "credit"),
                request.getRemark());
    }

    @Transactional
    public void allocate(AllocateQuotaRequest request) {
        permissionService.requireHeadquarters();
        Long tenantId = permissionService.required().getTenantId();
        QuotaAccount pool = ensureTenantPoolAccount(tenantId);
        QuotaAccount store = ensureStoreAccount(tenantId, request.getStoreId());
        String baseKey = requiredKey(request.getBizId(), "allocate");
        apply(tenantId, pool, -request.getAmount(), QuotaBizType.ALLOCATE_OUT,
                baseKey + ":out", baseKey + ":out", request.getRemark());
        apply(tenantId, store, request.getAmount(), QuotaBizType.ALLOCATE_IN,
                baseKey + ":in", baseKey + ":in", request.getRemark());
    }

    @Transactional
    public QuotaAccount creditTenantPool(Long tenantId, Long amount, String idempotentKey, String remark) {
        QuotaAccount pool = ensureTenantPoolAccount(tenantId);
        return credit(tenantId, pool, amount, idempotentKey, remark);
    }

    @Transactional
    public void charge(Long tenantId, Long storeId, Long amount, String idempotentKey, String remark) {
        if (amount == null || amount <= 0) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "扣减额度必须大于0");
        }
        charge(tenantId, ensureStoreAccount(tenantId, storeId), amount, idempotentKey, remark);
    }

    @Transactional
    public void consumeForAi(Long tenantId, Long storeId, Long amount, Long workId) {
        charge(tenantId, storeId, amount, "gen:" + workId, "AI生成扣费");
    }

    @Transactional
    public void refundForAi(Long tenantId, Long storeId, Long amount, Long taskId) {
        if (amount == null || amount <= 0) {
            return;
        }
        QuotaAccount account = ensureStoreAccount(tenantId, storeId);
        apply(tenantId, account, amount, QuotaBizType.REFUND, "refund:" + taskId,
                "refund:" + taskId, "AI生成失败退款");
    }

    public QuotaAccount getStore(Long storeId) {
        permissionService.requiredRead();
        return ensureStoreAccount(storeId);
    }

    public PageResult<QuotaFlow> flows(Long accountId, QuotaFlowQuery query) {
        AuthPrincipal principal = permissionService.requiredRead();
        QuotaAccount account = accountMapper.findById(principal.getTenantId(), accountId);
        if (account == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "额度账户不存在");
        }
        int pageNo = Math.max(query == null ? 1 : query.getPageNo(), 1);
        int pageSize = Math.min(Math.max(query == null ? 20 : query.getPageSize(), 1), MAX_PAGE_SIZE);
        String bizType = query == null ? null : query.getBizType();
        if (bizType != null && !bizType.trim().isEmpty()) {
            try {
                QuotaBizType.valueOf(bizType.trim().toUpperCase());
                bizType = bizType.trim().toUpperCase();
            } catch (IllegalArgumentException exception) {
                throw new BusinessException(ErrorCode.INVALID_PARAMETER, "流水业务类型不合法");
            }
        }
        int offset = (pageNo - 1) * pageSize;
        List<QuotaFlow> list = flowMapper.findByAccount(principal.getTenantId(), accountId, bizType,
                query == null ? null : query.getFrom(), query == null ? null : query.getTo(), offset, pageSize);
        return PageResult.of(list, flowMapper.countByAccount(principal.getTenantId(), accountId, bizType,
                query == null ? null : query.getFrom(), query == null ? null : query.getTo()), pageNo, pageSize);
    }

    public List<QuotaFlow> flows(Long accountId) {
        PageResult<QuotaFlow> page = flows(accountId, new QuotaFlowQuery());
        return page.getList();
    }

    public QuotaSummary my() {
        AuthPrincipal principal = permissionService.required();
        if (principal.getTenantId() == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "平台账号没有租户额度");
        }
        QuotaAccount account;
        if ("OWNER".equals(principal.getRole()) || "STAFF".equals(principal.getRole())) {
            account = ensureStoreAccount(principal.getTenantId(), principal.getOrgId());
        } else {
            account = ensureTenantPoolAccount(principal.getTenantId());
        }
        List<QuotaFlow> recent = flowMapper.findByAccount(principal.getTenantId(), account.getId(), null,
                null, null, 0, 10);
        return new QuotaSummary(account, recent == null ? Collections.emptyList() : recent);
    }

    private QuotaAccount credit(Long tenantId, QuotaAccount account, Long amount, String idempotentKey,
                                String remark) {
        if (amount == null || amount <= 0) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "充值额度必须大于0");
        }
        return apply(tenantId, account, amount, QuotaBizType.CREDIT, idempotentKey, idempotentKey, remark);
    }

    private QuotaAccount charge(Long tenantId, QuotaAccount account, Long amount, String idempotentKey,
                                String remark) {
        if (amount == null || amount <= 0) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "扣减额度必须大于0");
        }
        return apply(tenantId, account, -amount, QuotaBizType.CONSUME, idempotentKey, idempotentKey, remark);
    }

    private QuotaAccount apply(Long tenantId, QuotaAccount account, long amount, QuotaBizType type,
                               String idempotentKey, String bizId, String remark) {
        String key = requiredKey(idempotentKey, type.name().toLowerCase());
        QuotaFlow existing = flowMapper.findByIdempotentKey(key);
        if (existing != null) {
            verifyExisting(existing, tenantId, account.getId(), type, amount);
            return accountMapper.findById(tenantId, account.getId());
        }

        QuotaFlow flow = flow(tenantId, account.getId(), type, bizId, amount, key, remark);
        if (flowMapper.insert(flow) == 0) {
            existing = flowMapper.findByIdempotentKey(key);
            if (existing == null) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "流水幂等校验失败");
            }
            verifyExisting(existing, tenantId, account.getId(), type, amount);
            return accountMapper.findById(tenantId, account.getId());
        }

        for (int attempt = 0; attempt < MAX_RETRY; attempt++) {
            QuotaAccount current = accountMapper.findById(tenantId, account.getId());
            if (current == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "额度账户不存在");
            }
            if (amount < 0 && current.getBalance() < -amount) {
                throw new BusinessException(ErrorCode.QUOTA_NOT_ENOUGH);
            }
            if (accountMapper.changeBalanceWithVersion(tenantId, current.getId(), current.getVersion(), amount) == 1) {
                long balanceAfter = current.getBalance() + amount;
                flowMapper.updateBalanceAfter(tenantId, flow.getId(), balanceAfter);
                current.setBalance(balanceAfter);
                current.setVersion(current.getVersion() + 1);
                return current;
            }
        }
        throw new BusinessException(ErrorCode.SYSTEM_ERROR, "余额更新冲突，请重试");
    }

    private void verifyExisting(QuotaFlow existing, Long tenantId, Long accountId, QuotaBizType type,
                                long amount) {
        if (!tenantId.equals(existing.getTenantId()) || !accountId.equals(existing.getAccountId())
                || !type.name().equals(existing.getBizType()) || amount != existing.getAmount()) {
            throw new BusinessException(ErrorCode.DUPLICATE, "幂等键已被其他账务操作使用");
        }
    }

    private QuotaFlow flow(Long tenantId, Long accountId, QuotaBizType type, String bizId, long amount,
                           String idempotentKey, String remark) {
        QuotaFlow flow = new QuotaFlow();
        flow.setTenantId(tenantId);
        flow.setAccountId(accountId);
        flow.setBizType(type.name());
        flow.setBizId(bizId);
        flow.setAmount(amount);
        flow.setIdempotentKey(idempotentKey);
        flow.setRemark(remark);
        return flow;
    }

    private String requiredKey(String value, String prefix) {
        String key = value == null || value.trim().isEmpty()
                ? prefix + ":" + UUID.randomUUID().toString().replace("-", "") : value.trim();
        if (key.length() > 64) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "幂等键长度不能超过64个字符");
        }
        return key;
    }
}
