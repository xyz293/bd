package com.xiaoa.tenant.service;

import com.xiaoa.common.auth.AuthContext;
import com.xiaoa.common.auth.AuthPrincipal;
import com.xiaoa.common.exception.BusinessException;
import com.xiaoa.common.exception.ErrorCode;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Service
public class PermissionService {

    private static final Set<String> ADMIN_ROLES = new HashSet<>(Arrays.asList(
            "HQ_ADMIN", "REGION_ADMIN", "OWNER"
    ));

    public AuthPrincipal required() {
        return AuthContext.required();
    }

    public AuthPrincipal requiredAdmin() {
        AuthPrincipal principal = required();
        if (!ADMIN_ROLES.contains(principal.getRole())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return principal;
    }

    public void requireTenant(Long tenantId) {
        AuthPrincipal principal = required();
        if (!principal.getTenantId().equals(tenantId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    public boolean canManageOrg(Long orgId) {
        AuthPrincipal principal = requiredAdmin();
        if ("HQ_ADMIN".equals(principal.getRole())) {
            return true;
        }
        return principal.getOrgId().equals(orgId) || "REGION_ADMIN".equals(principal.getRole());
    }
}
