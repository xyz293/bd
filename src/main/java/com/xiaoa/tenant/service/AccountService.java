package com.xiaoa.tenant.service;

import com.xiaoa.common.auth.AuthPrincipal;
import com.xiaoa.common.auth.SessionService;
import com.xiaoa.common.exception.BusinessException;
import com.xiaoa.common.exception.ErrorCode;
import com.xiaoa.tenant.dto.JoinStoreRequest;
import com.xiaoa.tenant.dto.JoinStoreResponse;
import com.xiaoa.tenant.dto.LoginRequest;
import com.xiaoa.tenant.dto.TakeoverRequest;
import com.xiaoa.tenant.dto.TokenResponse;
import com.xiaoa.tenant.mapper.OrgMapper;
import com.xiaoa.tenant.mapper.TenantMapper;
import com.xiaoa.tenant.mapper.UserMapper;
import com.xiaoa.tenant.mapper.UserOrgRoleMapper;
import com.xiaoa.tenant.mapper.UserWechatBindMapper;
import com.xiaoa.tenant.model.InviteCode;
import com.xiaoa.tenant.model.Org;
import com.xiaoa.tenant.model.Tenant;
import com.xiaoa.tenant.model.UserAccount;
import com.xiaoa.tenant.model.UserOrgRole;
import com.xiaoa.tenant.model.UserWechatBind;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AccountService {

    private final UserMapper userMapper;
    private final UserOrgRoleMapper userOrgRoleMapper;
    private final UserWechatBindMapper userWechatBindMapper;
    private final TenantMapper tenantMapper;
    private final OrgMapper orgMapper;
    private final InviteService inviteService;
    private final SessionService sessionService;
    private final PermissionService permissionService;

    public AccountService(UserMapper userMapper, UserOrgRoleMapper userOrgRoleMapper,
                          UserWechatBindMapper userWechatBindMapper, TenantMapper tenantMapper,
                          OrgMapper orgMapper, InviteService inviteService, SessionService sessionService,
                          PermissionService permissionService) {
        this.userMapper = userMapper;
        this.userOrgRoleMapper = userOrgRoleMapper;
        this.userWechatBindMapper = userWechatBindMapper;
        this.tenantMapper = tenantMapper;
        this.orgMapper = orgMapper;
        this.inviteService = inviteService;
        this.sessionService = sessionService;
        this.permissionService = permissionService;
    }

    public TokenResponse login(LoginRequest request) {
        UserAccount user = userMapper.findByOpenid(request.getOpenid());
        if (user == null) {
            throw new BusinessException(ErrorCode.NEED_JOIN);
        }
        return loginUser(user);
    }

    @Transactional
    public JoinStoreResponse join(JoinStoreRequest request) {
        InviteCode inviteCode = inviteService.consume(request.getCode());
        UserAccount user = userMapper.findByOpenid(request.getOpenid());
        if (user == null) {
            user = userMapper.findByPhone(request.getPhone());
            if (user == null) {
                user = new UserAccount();
                user.setPhone(request.getPhone());
                user.setOpenid(request.getOpenid());
                user.setNickname(request.getNickname());
                userMapper.insert(user);
            } else if (user.getOpenid() != null && !request.getOpenid().equals(user.getOpenid())) {
                throw new BusinessException(ErrorCode.PHONE_ACCOUNT_EXISTS);
            } else {
                userMapper.updateOpenid(user.getId(), request.getOpenid());
            }
        }
        ensureActiveUser(user);
        List<UserOrgRole> activeRoles = userOrgRoleMapper.findActiveByUserId(user.getId());
        if (!activeRoles.isEmpty()) {
            throw new BusinessException(ErrorCode.ALREADY_JOINED);
        }
        UserOrgRole role = new UserOrgRole();
        role.setTenantId(inviteCode.getTenantId());
        role.setUserId(user.getId());
        role.setOrgId(inviteCode.getStoreId());
        role.setRole(inviteCode.getRole());
        role.setDataScope(4);
        userOrgRoleMapper.insert(role);
        TokenResponse login = loginUser(user, inviteCode.getTenantId(), inviteCode.getStoreId(),
                inviteCode.getRole(), 4);
        return new JoinStoreResponse(login, inviteCode.getStoreId());
    }

    @Transactional
    public TokenResponse takeover(TakeoverRequest request) {
        UserAccount user = userMapper.findByPhone(request.getPhone());
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "手机号对应的账号不存在");
        }
        UserAccount sameOpenid = userMapper.findByOpenid(request.getOpenid());
        if (sameOpenid != null && !sameOpenid.getId().equals(user.getId())) {
            throw new BusinessException(ErrorCode.DUPLICATE, "微信已绑定其他账号");
        }
        if (user.getOpenid() != null && user.getOpenid().equals(request.getOpenid())) {
            return loginUser(user);
        }
        UserWechatBind bind = new UserWechatBind();
        bind.setUserId(user.getId());
        bind.setOpenid(request.getOpenid());
        bind.setAction(3);
        userWechatBindMapper.insert(bind);
        userMapper.updateOpenid(user.getId(), request.getOpenid());
        return loginUser(user);
    }

    @Transactional
    public void remove(Long roleId) {
        AuthPrincipal principal = requireManager();
        UserOrgRole role = userOrgRoleMapper.findById(roleId, principal.getTenantId());
        if (role == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "成员关系不存在");
        }
        if ("STAFF".equals(principal.getRole()) || ("OWNER".equals(principal.getRole())
                && !principal.getOrgId().equals(role.getOrgId()))) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        userOrgRoleMapper.updateStatus(roleId, principal.getTenantId(), 2);
    }

    @Transactional
    public void disable(Long userId) {
        requireManager();
        UserAccount user = userMapper.findById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        userMapper.updateStatus(userId, 2);
    }

    @Transactional
    public void updateRole(Long roleId, String role, Integer dataScope) {
        AuthPrincipal principal = requireManager();
        UserOrgRole current = userOrgRoleMapper.findById(roleId, principal.getTenantId());
        if (current == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "成员关系不存在");
        }
        if (!"HQ_ADMIN".equals(principal.getRole()) && !principal.getOrgId().equals(current.getOrgId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        userOrgRoleMapper.updateRole(roleId, principal.getTenantId(), role, dataScope);
    }

    private AuthPrincipal requireManager() {
        return permissionService.requiredAdmin();
    }

    private TokenResponse loginUser(UserAccount user) {
        List<UserOrgRole> roles = userOrgRoleMapper.findActiveByUserId(user.getId());
        if (roles.isEmpty()) {
            throw new BusinessException(ErrorCode.NEED_JOIN);
        }
        UserOrgRole role = roles.get(0);
        return loginUser(user, role.getTenantId(), role.getOrgId(), role.getRole(), role.getDataScope());
    }

    private TokenResponse loginUser(UserAccount user, Long tenantId, Long orgId, String role, Integer dataScope) {
        ensureActiveUser(user);
        Tenant tenant = tenantMapper.findById(tenantId);
        if (tenant == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "租户不存在");
        }
        if (tenant.getStatus() != null && tenant.getStatus() == 2) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "租户已停用");
        }
        if (tenant.getExpireAt() != null && tenant.getExpireAt().isBefore(java.time.LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.TENANT_EXPIRED);
        }
        Org org = orgMapper.findById(orgId, tenantId);
        AuthPrincipal principal = new AuthPrincipal(user.getId(), tenantId, orgId, role, dataScope);
        String token = sessionService.create(principal);
        return new TokenResponse(token, user.getId(), tenantId, orgId, role, dataScope,
                tenant.getStatus(), tenant.getName(), org == null ? null : org.getName());
    }

    private void ensureActiveUser(UserAccount user) {
        if (user.getStatus() != null && user.getStatus() == 2) {
            throw new BusinessException(ErrorCode.USER_DISABLED);
        }
    }
}
