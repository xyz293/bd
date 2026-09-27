package com.xiaoa.quota.service;

import com.xiaoa.common.api.PageResult;
import com.xiaoa.common.auth.AdminPermissionService;
import com.xiaoa.common.auth.AuthPrincipal;
import com.xiaoa.common.exception.BusinessException;
import com.xiaoa.common.exception.ErrorCode;
import com.xiaoa.quota.dto.AllocateQuotaRequest;
import com.xiaoa.quota.dto.AllocateStaffQuotaRequest;
import com.xiaoa.quota.dto.CreditQuotaRequest;
import com.xiaoa.quota.dto.QuotaFlowQuery;
import com.xiaoa.quota.dto.RecallStaffQuotaRequest;
import com.xiaoa.quota.mapper.QuotaAccountMapper;
import com.xiaoa.quota.mapper.QuotaFlowMapper;
import com.xiaoa.quota.model.QuotaAccount;
import com.xiaoa.quota.model.QuotaBizType;
import com.xiaoa.quota.model.QuotaFlow;
import com.xiaoa.quota.model.QuotaSummary;
import com.xiaoa.tenant.mapper.UserOrgRoleMapper;
import com.xiaoa.tenant.model.UserOrgRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * 三级额度账户：TENANT 租户池 → STORE 门店账户 → STAFF 员工账户。
 * STAFF 级 owner_id = user_org_role.id（成员关系 ID，一人多店各有账户）。
 * 所有账务变更单入口 apply()：幂等流水 + 乐观锁 + 余额预检。
 */
@Service
public class QuotaService {

    private static final int MAX_RETRY = 3;
    private static final int MAX_PAGE_SIZE = 200;

    private final QuotaAccountMapper accountMapper;
    private final QuotaFlowMapper flowMapper;
    private final AdminPermissionService permissionService;
    private final UserOrgRoleMapper userOrgRoleMapper;

    public QuotaService(QuotaAccountMapper accountMapper, QuotaFlowMapper flowMapper,
                        AdminPermissionService permissionService, UserOrgRoleMapper userOrgRoleMapper) {
        this.accountMapper = accountMapper;
        this.flowMapper = flowMapper;
        this.permissionService = permissionService;
        this.userOrgRoleMapper = userOrgRoleMapper;
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

    /**
     * 员工账户懒建（幂等）：入店流程主动调用，划拨时兜底。
     */
    @Transactional
    public QuotaAccount ensureStaffAccount(Long tenantId, Long memberRoleId) {
        if (tenantId == null || memberRoleId == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "成员信息不能为空");
        }
        QuotaAccount account = accountMapper.findByOwner(tenantId, "STAFF", memberRoleId);
        if (account != null) {
            return account;
        }
        QuotaAccount created = new QuotaAccount();
        created.setTenantId(tenantId);
        created.setLevel("STAFF");
        created.setOwnerId(memberRoleId);
        created.setBalance(0L);
        created.setVersion(0);
        accountMapper.ensure(created);
        account = accountMapper.findByOwner(tenantId, "STAFF", memberRoleId);
        if (account == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "员工额度账户创建失败");
        }
        return account;
    }

    /**
     * 店长向员工划拨：一条事务双流水（门店 ALLOCATE_OUT / 员工 ALLOCATE_IN），
     * 门店池余额不足时整体失败（乐观锁 WHERE balance>= 兜底）。
     */
    @Transactional
    public void allocateToStaff(AuthPrincipal principal, AllocateStaffQuotaRequest request) {
        if (principal == null || !"OWNER".equals(principal.getRole()) || principal.getOrgId() == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅店长可划拨员工额度");
        }
        UserOrgRole memberRole = requireStaffRole(principal, request.getMemberRoleId());
        Long tenantId = principal.getTenantId();
        QuotaAccount store = ensureStoreAccount(tenantId, principal.getOrgId());
        QuotaAccount staff = ensureStaffAccount(tenantId, memberRole.getId());
        String baseKey = requiredKey(request.getBizId(), "alloc");
        apply(tenantId, store, -request.getAmount(), QuotaBizType.ALLOCATE_OUT,
                baseKey + ":out", baseKey + ":out", request.getRemark());
        apply(tenantId, staff, request.getAmount(), QuotaBizType.ALLOCATE_IN,
                baseKey + ":in", baseKey + ":in", request.getRemark());
    }

    /**
     * 店长回收员工未用额度：RECALL 双流水，回收金额超过员工余额即失败。
     */
    @Transactional
    public void recallFromStaff(AuthPrincipal principal, RecallStaffQuotaRequest request) {
        if (principal == null || !"OWNER".equals(principal.getRole()) || principal.getOrgId() == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅店长可回收员工额度");
        }
        UserOrgRole memberRole = requireStaffRole(principal, request.getMemberRoleId());
        Long tenantId = principal.getTenantId();
        QuotaAccount staff = ensureStaffAccount(tenantId, memberRole.getId());
        QuotaAccount store = ensureStoreAccount(tenantId, principal.getOrgId());
        String baseKey = requiredKey(request.getBizId(), "recall");
        apply(tenantId, staff, -request.getAmount(), QuotaBizType.RECALL,
                baseKey + ":out", baseKey + ":out", request.getRemark());
        apply(tenantId, store, request.getAmount(), QuotaBizType.RECALL,
                baseKey + ":in", baseKey + ":in", request.getRemark());
    }

    private UserOrgRole requireStaffRole(AuthPrincipal principal, Long memberRoleId) {
        if (memberRoleId == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "成员不能为空");
        }
        UserOrgRole role = userOrgRoleMapper.findById(memberRoleId, principal.getTenantId());
        if (role == null || role.getOrgId() == null || !role.getOrgId().equals(principal.getOrgId())
                || !"STAFF".equals(role.getRole()) || role.getStatus() == null || role.getStatus() != 1) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "员工不存在或不属于本店");
        }
        return role;
    }

    /**
     * 解析扣费账户：员工（STAFF）优先扣自己的员工账户，无账户（老数据/灰度期）回退门店账户；
     * 店长及其他角色扣门店账户。
     */
    public Long resolveChargeAccountId(Long tenantId, Long storeId, Long userId, String role) {
        if ("STAFF".equals(role) && userId != null && storeId != null) {
            UserOrgRole roleRec = userOrgRoleMapper.findByUserOrgRole(tenantId, userId, storeId, "STAFF");
            if (roleRec != null) {
                QuotaAccount staff = accountMapper.findByOwner(tenantId, "STAFF", roleRec.getId());
                if (staff != null) {
                    return staff.getId();
                }
            }
        }
        return ensureStoreAccount(tenantId, storeId).getId();
    }

    /**
     * 按账户 ID 扣费（对话/AI 生成统一入口），幂等键防重。
     */
    public void chargeAccount(Long tenantId, Long accountId, Long amount, String idempotentKey, String remark) {
        if (amount == null || amount <= 0) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "扣减额度必须大于0");
        }
        QuotaAccount account = accountMapper.findById(tenantId, accountId);
        if (account == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "额度账户不存在");
        }
        charge(tenantId, account, amount, idempotentKey, remark);
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

    /**
     * AI 生成扣费：员工扣员工账户，无员工账户回退门店（灰度平滑）。
     */
    @Transactional
    public void consumeForAi(Long tenantId, Long storeId, Long userId, String role, Long amount, Long workId) {
        Long accountId = resolveChargeAccountId(tenantId, storeId, userId, role);
        chargeAccount(tenantId, accountId, amount, "gen:" + workId, "AI生成扣费");
    }

    /**
     * AI 生成失败退款：按扣费流水（CONSUME / bizId=gen:{workId}）的账户退，
     * 流水缺失时回退门店账户。幂等键保持 refund:{taskId}。
     */
    @Transactional
    public void refundForAi(Long tenantId, Long storeId, Long amount, Long workId, Long taskId) {
        if (amount == null || amount <= 0) {
            return;
        }
        Long accountId = null;
        QuotaFlow consume = flowMapper.findByBizTypeAndBizId(tenantId, QuotaBizType.CONSUME.name(), "gen:" + workId);
        if (consume != null) {
            accountId = consume.getAccountId();
        }
        QuotaAccount account = accountId != null
                ? accountMapper.findById(tenantId, accountId)
                : ensureStoreAccount(tenantId, storeId);
        if (account == null) {
            account = ensureStoreAccount(tenantId, storeId);
        }
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

    /**
     * 我的额度：员工优先自己的 STAFF 账户（无则回退门店），店长看门店，管理层看租户池。
     */
    public QuotaSummary my() {
        AuthPrincipal principal = permissionService.required();
        if (principal.getTenantId() == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "平台账号没有租户额度");
        }
        QuotaAccount account;
        if ("STAFF".equals(principal.getRole()) && principal.getOrgId() != null) {
            account = findStaffAccountOrNull(principal.getTenantId(), principal.getUserId(), principal.getOrgId());
            if (account == null) {
                account = ensureStoreAccount(principal.getTenantId(), principal.getOrgId());
            }
        } else if ("OWNER".equals(principal.getRole())) {
            account = ensureStoreAccount(principal.getTenantId(), principal.getOrgId());
        } else {
            account = ensureTenantPoolAccount(principal.getTenantId());
        }
        List<QuotaFlow> recent = flowMapper.findByAccount(principal.getTenantId(), account.getId(), null,
                null, null, 0, 10);
        return new QuotaSummary(account, recent == null ? Collections.emptyList() : recent);
    }

    private QuotaAccount findStaffAccountOrNull(Long tenantId, Long userId, Long storeId) {
        UserOrgRole roleRec = userOrgRoleMapper.findByUserOrgRole(tenantId, userId, storeId, "STAFF");
        if (roleRec == null) {
            return null;
        }
        return accountMapper.findByOwner(tenantId, "STAFF", roleRec.getId());
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
