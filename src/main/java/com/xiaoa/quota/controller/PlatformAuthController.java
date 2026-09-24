package com.xiaoa.quota.controller;

import com.xiaoa.common.api.Result;
import com.xiaoa.common.auth.AuthPrincipal;
import com.xiaoa.common.auth.SessionService;
import com.xiaoa.common.exception.BusinessException;
import com.xiaoa.common.exception.ErrorCode;
import com.xiaoa.quota.dto.PlatformLoginRequest;
import com.xiaoa.quota.dto.PlatformTokenResponse;
import com.xiaoa.quota.mapper.PlatformUserMapper;
import com.xiaoa.quota.model.PlatformUser;
import org.springframework.dao.DataAccessException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/platform/auth")
@Validated
public class PlatformAuthController {

    private final PlatformUserMapper platformUserMapper;
    private final SessionService sessionService;

    public PlatformAuthController(PlatformUserMapper platformUserMapper, SessionService sessionService) {
        this.platformUserMapper = platformUserMapper;
        this.sessionService = sessionService;
    }

    @PostMapping("/login")
    public Result<PlatformTokenResponse> login(@Valid @RequestBody PlatformLoginRequest request) {
        PlatformUser user;
        try {
            user = platformUserMapper.findByLoginName(request.getLoginName());
        } catch (DataAccessException exception) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "平台账号表未初始化");
        }
        if (user == null || user.getStatus() == null || user.getStatus() != 1
                || !sha256(request.getCredential()).equalsIgnoreCase(user.getCredentialHash())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "平台账号或密码错误");
        }
        AuthPrincipal principal = new AuthPrincipal(user.getId(), null, null, user.getRole(), 1);
        String token = sessionService.create(principal);
        return Result.success(new PlatformTokenResponse(token, user.getId(), user.getRole()));
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte item : digest) {
                result.append(String.format("%02x", item));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }
}
