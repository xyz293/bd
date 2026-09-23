package com.xiaoa.quota.service;

import com.xiaoa.common.auth.AdminPermissionService;
import com.xiaoa.common.exception.BusinessException;
import com.xiaoa.common.exception.ErrorCode;
import com.xiaoa.quota.dto.AllocateQuotaRequest;
import com.xiaoa.quota.dto.CreditQuotaRequest;
import com.xiaoa.quota.mapper.QuotaAccountMapper;
import com.xiaoa.quota.mapper.QuotaFlowMapper;
import com.xiaoa.quota.model.QuotaAccount;
import com.xiaoa.quota.model.QuotaFlow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class QuotaService {

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
        return accountMapper.findByOwner(tenantId, "STORE", storeId);
    }

    @Transactional
    public QuotaAccount credit(CreditQuotaRequest request) {
        permissionService.requireHeadquarters();
        Long tenantId = permissionService.required().getTenantId();
        QuotaAccount account = accountMapper.findById(tenantId, request.getAccountId());
        if (account == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "额度账户不存在");
        }
        String bizId = bizId(request.getBizId(), "credit");
        if (accountMapper.changeBalance(tenantId, account.getId(), request.getAmount()) != 1) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "额度账户更新失败");
        }
        QuotaFlow flow = flow(tenantId, account.getId(), "CREDIT", bizId, request.getAmount(), request.getRemark());
        flowMapper.insert(flow);
        return accountMapper.findById(tenantId, account.getId());
    }

    @Transactional
    public void allocate(AllocateQuotaRequest request) {
        permissionService.requireHeadquarters();
        Long tenantId = permissionService.required().getTenantId();
        QuotaAccount pool = accountMapper.findByOwner(tenantId, "TENANT", tenantId);
        QuotaAccount store = accountMapper.findByOwner(tenantId, "STORE", request.getStoreId());
        if (pool == null || store == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "总池或门店额度账户不存在");
        }
        String bizId = bizId(request.getBizId(), "allocate");
        if (accountMapper.changeBalance(tenantId, pool.getId(), -request.getAmount()) != 1) {
            throw new BusinessException(ErrorCode.QUOTA_NOT_ENOUGH);
        }
        if (accountMapper.changeBalance(tenantId, store.getId(), request.getAmount()) != 1) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "门店额度更新失败");
        }
        flowMapper.insert(flow(tenantId, pool.getId(), "ALLOCATE", bizId + ":pool", -request.getAmount(), request.getRemark()));
        flowMapper.insert(flow(tenantId, store.getId(), "ALLOCATE", bizId + ":store", request.getAmount(), request.getRemark()));
    }

    public QuotaAccount getStore(Long storeId) {
        return ensureStoreAccount(storeId);
    }

    @Transactional
    public void consumeForAi(Long tenantId, Long storeId, Long amount, Long workId) {
        if (amount == null || amount <= 0) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "生成费用必须大于0");
        }
        QuotaAccount account = ensureStoreAccount(tenantId, storeId);
        String bizId = "AI:CONSUME:" + workId;
        if (flowMapper.findByAccountBiz(tenantId, account.getId(), bizId) != null) {
            return;
        }
        if (accountMapper.changeBalance(tenantId, account.getId(), -amount) != 1) {
            throw new BusinessException(ErrorCode.QUOTA_NOT_ENOUGH);
        }
        flowMapper.insert(flow(tenantId, account.getId(), "AI_CONSUME", bizId, -amount, "AI生成扣费"));
    }

    @Transactional
    public void refundForAi(Long tenantId, Long storeId, Long amount, Long workId) {
        if (amount == null || amount <= 0) {
            return;
        }
        QuotaAccount account = ensureStoreAccount(tenantId, storeId);
        String bizId = "AI:REFUND:" + workId;
        if (flowMapper.findByAccountBiz(tenantId, account.getId(), bizId) != null) {
            return;
        }
        if (accountMapper.changeBalance(tenantId, account.getId(), amount) != 1) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI退款更新额度失败");
        }
        flowMapper.insert(flow(tenantId, account.getId(), "AI_REFUND", bizId, amount, "AI生成失败退款"));
    }

    private QuotaAccount ensureStoreAccount(Long tenantId, Long storeId) {
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
        accountMapper.ensure(created);
        account = accountMapper.findByOwner(tenantId, "STORE", storeId);
        if (account == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "额度账户创建失败");
        }
        return account;
    }

    public List<QuotaFlow> flows(Long accountId) {
        Long tenantId = permissionService.requiredRead().getTenantId();
        return flowMapper.findByAccount(tenantId, accountId);
    }

    private QuotaFlow flow(Long tenantId, Long accountId, String type, String bizId, Long amount, String remark) {
        QuotaFlow flow = new QuotaFlow();
        flow.setTenantId(tenantId);
        flow.setAccountId(accountId);
        flow.setBizType(type);
        flow.setBizId(bizId);
        flow.setAmount(amount);
        flow.setRemark(remark);
        return flow;
    }

    private String bizId(String value, String prefix) {
        return value == null || value.trim().isEmpty() ? prefix + ":" + UUID.randomUUID() : value;
    }
}
