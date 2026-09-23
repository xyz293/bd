package com.xiaoa.tenant.controller;

import com.xiaoa.common.api.Result;
import com.xiaoa.common.auth.AuthContext;
import com.xiaoa.common.auth.AuthPrincipal;
import com.xiaoa.common.auth.SessionService;
import com.xiaoa.tenant.dto.JoinStoreRequest;
import com.xiaoa.tenant.dto.JoinStoreResponse;
import com.xiaoa.tenant.dto.LoginRequest;
import com.xiaoa.tenant.dto.TakeoverRequest;
import com.xiaoa.tenant.dto.TokenResponse;
import com.xiaoa.tenant.service.AccountService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@Validated
public class AuthController {

    private final AccountService accountService;
    private final SessionService sessionService;

    public AuthController(AccountService accountService, SessionService sessionService) {
        this.accountService = accountService;
        this.sessionService = sessionService;
    }

    @PostMapping("/login")
    public Result<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(accountService.login(request));
    }

    @PostMapping("/join")
    public Result<JoinStoreResponse> join(@Valid @RequestBody JoinStoreRequest request) {
        return Result.success(accountService.join(request));
    }

    @PostMapping("/takeover")
    public Result<TokenResponse> takeover(@Valid @RequestBody TakeoverRequest request) {
        return Result.success(accountService.takeover(request));
    }

    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        sessionService.delete(resolveToken(authorization));
        return Result.success();
    }

    @GetMapping("/me")
    public Result<Map<String, Object>> me() {
        AuthPrincipal principal = AuthContext.required();
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("userId", principal.getUserId());
        result.put("tenantId", principal.getTenantId());
        result.put("orgId", principal.getOrgId());
        result.put("role", principal.getRole());
        result.put("dataScope", principal.getDataScope());
        return Result.success(result);
    }

    private String resolveToken(String authorization) {
        if (authorization == null || authorization.trim().isEmpty()) {
            return null;
        }
        return authorization.startsWith("Bearer ")
                ? authorization.substring("Bearer ".length()).trim() : authorization.trim();
    }
}
