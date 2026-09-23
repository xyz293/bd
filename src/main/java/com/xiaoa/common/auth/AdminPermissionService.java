package com.xiaoa.common.auth;

import com.xiaoa.common.exception.BusinessException;
import com.xiaoa.common.exception.ErrorCode;
import org.springframework.stereotype.Service;

@Service
public class AdminPermissionService {

    public AuthPrincipal required() {
        return AuthContext.required();
    }

    public AuthPrincipal requiredRead() {
        AuthPrincipal principal = required();
        if (!"HQ_ADMIN".equals(principal.getRole())
                && !"REGION_ADMIN".equals(principal.getRole())
                && !"VIEWER".equals(principal.getRole())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "当前角色不能访问企业版管理端");
        }
        return principal;
    }

    public AuthPrincipal requiredWrite() {
        AuthPrincipal principal = required();
        if (!"HQ_ADMIN".equals(principal.getRole()) && !"REGION_ADMIN".equals(principal.getRole())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "当前角色没有管理端配置权限");
        }
        return principal;
    }

    public void requireHeadquarters() {
        if (!"HQ_ADMIN".equals(required().getRole())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅总部管理员可执行此操作");
        }
    }

    public void requireTenant(Long tenantId) {
        if (!required().getTenantId().equals(tenantId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }
}
