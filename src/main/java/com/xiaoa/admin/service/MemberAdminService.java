package com.xiaoa.admin.service;

import com.xiaoa.common.auth.AdminPermissionService;
import com.xiaoa.common.auth.AuthPrincipal;
import com.xiaoa.common.api.PageResult;
import com.xiaoa.tenant.mapper.UserMapper;
import com.xiaoa.tenant.model.UserAccount;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MemberAdminService {

    private final UserMapper userMapper;
    private final AdminPermissionService permissionService;

    public MemberAdminService(UserMapper userMapper, AdminPermissionService permissionService) {
        this.userMapper = userMapper;
        this.permissionService = permissionService;
    }

    public PageResult<UserAccount> list(Long orgId, int pageNo, int pageSize) {
        AuthPrincipal principal = permissionService.requiredRead();
        int safePageNo = Math.max(1, pageNo);
        int safePageSize = Math.min(100, Math.max(1, pageSize));
        int offset = (safePageNo - 1) * safePageSize;
        List<UserAccount> users = userMapper.findByTenantOrg(principal.getTenantId(), orgId, offset, safePageSize);
        long total = userMapper.countByTenantOrg(principal.getTenantId(), orgId);
        return PageResult.of(users, total, safePageNo, safePageSize);
    }
}
