package com.xiaoa.common.auth;

import com.xiaoa.common.exception.BusinessException;
import com.xiaoa.common.exception.ErrorCode;

public final class AuthContext {

    private static final ThreadLocal<AuthPrincipal> PRINCIPAL = new ThreadLocal<>();

    private AuthContext() {
    }

    public static void set(AuthPrincipal principal) {
        PRINCIPAL.set(principal);
    }

    public static AuthPrincipal get() {
        return PRINCIPAL.get();
    }

    public static AuthPrincipal required() {
        AuthPrincipal principal = get();
        if (principal == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return principal;
    }

    public static void clear() {
        PRINCIPAL.remove();
    }
}
