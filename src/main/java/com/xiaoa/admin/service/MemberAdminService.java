package com.xiaoa.admin.service;

import com.xiaoa.common.auth.AdminPermissionService;
import com.xiaoa.common.auth.AuthPrincipal;
import com.xiaoa.common.api.PageResult;
import com.xiaoa.quota.mapper.QuotaAccountMapper;
import com.xiaoa.quota.model.QuotaAccount;
import com.xiaoa.tenant.mapper.UserMapper;
import com.xiaoa.tenant.mapper.UserOrgRoleMapper;
import com.xiaoa.tenant.model.UserAccount;
import com.xiaoa.tenant.model.UserOrgRole;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 管理端成员查询：附加每人额度余额列。
 * 余额规则：STAFF 取员工账户（owner=user_org_role.id，无账户记 0）；
 * OWNER 取所在门店账户；管理层（HQ/REGION/VIEWER）取租户池。
 */
@Service
public class MemberAdminService {

    private final UserMapper userMapper;
    private final AdminPermissionService permissionService;
    private final UserOrgRoleMapper userOrgRoleMapper;
    private final QuotaAccountMapper quotaAccountMapper;

    public MemberAdminService(UserMapper userMapper, AdminPermissionService permissionService,
                              UserOrgRoleMapper userOrgRoleMapper, QuotaAccountMapper quotaAccountMapper) {
        this.userMapper = userMapper;
        this.permissionService = permissionService;
        this.userOrgRoleMapper = userOrgRoleMapper;
        this.quotaAccountMapper = quotaAccountMapper;
    }

    public PageResult<UserAccount> list(Long orgId, int pageNo, int pageSize) {
        AuthPrincipal principal = permissionService.requiredRead();
        int safePageNo = Math.max(1, pageNo);
        int safePageSize = Math.min(100, Math.max(1, pageSize));
        int offset = (safePageNo - 1) * safePageSize;
        List<UserAccount> users = userMapper.findByTenantOrg(principal.getTenantId(), orgId, offset, safePageSize);
        long total = userMapper.countByTenantOrg(principal.getTenantId(), orgId);
        for (UserAccount user : users) {
            user.setQuotaBalance(resolveBalance(principal.getTenantId(), user.getId(), user));
        }
        return PageResult.of(users, total, safePageNo, safePageSize);
    }

    private Long resolveBalance(Long tenantId, Long userId, UserAccount user) {
        List<UserOrgRole> roles = userOrgRoleMapper.findActiveByUserId(userId);
        Long staffRoleId = null;
        Long ownerOrgId = null;
        boolean managerRole = false;
        for (UserOrgRole role : roles) {
            if (!tenantId.equals(role.getTenantId())) {
                continue;
            }
            if ("STAFF".equals(role.getRole()) && staffRoleId == null) {
                staffRoleId = role.getId();
            } else if ("OWNER".equals(role.getRole()) && ownerOrgId == null) {
                ownerOrgId = role.getOrgId();
            } else if ("HQ_ADMIN".equals(role.getRole()) || "REGION_ADMIN".equals(role.getRole())
                    || "VIEWER".equals(role.getRole())) {
                managerRole = true;
            }
        }
        if (staffRoleId != null) {
            user.setUserOrgRoleId(staffRoleId);
            QuotaAccount staff = quotaAccountMapper.findByOwner(tenantId, "STAFF", staffRoleId);
            return staff == null ? 0L : staff.getBalance();
        }
        if (ownerOrgId != null) {
            QuotaAccount store = quotaAccountMapper.findByOwner(tenantId, "STORE", ownerOrgId);
            return store == null ? 0L : store.getBalance();
        }
        if (managerRole) {
            QuotaAccount pool = quotaAccountMapper.findByOwner(tenantId, "TENANT", tenantId);
            return pool == null ? 0L : pool.getBalance();
        }
        return 0L;
    }
}
