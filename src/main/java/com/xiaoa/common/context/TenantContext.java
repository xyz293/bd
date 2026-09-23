package com.xiaoa.common.context;

import com.xiaoa.common.exception.BusinessException;
import com.xiaoa.common.exception.ErrorCode;

public final class TenantContext {

    private static final ThreadLocal<Long> TENANT_ID = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void setTenantId(Long tenantId) {
        TENANT_ID.set(tenantId);
    }

    public static Long getTenantId() {
        return TENANT_ID.get();
    }

    public static long requiredTenantId() {
        Long tenantId = getTenantId();
        if (tenantId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "缺少租户上下文");
        }
        return tenantId;
    }

    public static void clear() {
        TENANT_ID.remove();
    }
}
