package com.xiaoa.tenant.service;

import com.xiaoa.common.auth.AuthPrincipal;
import com.xiaoa.common.exception.BusinessException;
import com.xiaoa.common.exception.ErrorCode;
import com.xiaoa.tenant.dto.UpdateRoleRequest;
import com.xiaoa.tenant.mapper.OrgMapper;
import com.xiaoa.tenant.mapper.UserMapper;
import com.xiaoa.tenant.mapper.UserOrgRoleMapper;
import com.xiaoa.tenant.model.UserOrgRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoleService {

    private final UserMapper userMapper;
    private final UserOrgRoleMapper userOrgRoleMapper;
    private final OrgMapper orgMapper;
    private final PermissionService permissionService;

    public RoleService(UserMapper userMapper, UserOrgRoleMapper userOrgRoleMapper,
                       OrgMapper orgMapper, PermissionService permissionService) {
        this.userMapper = userMapper;
        this.userOrgRoleMapper = userOrgRoleMapper;
        this.orgMapper = orgMapper;
        this.permissionService = permissionService;
    }

    @Transactional
    public void grant(Long userId, UpdateRoleRequest request) {
        AuthPrincipal principal = permissionService.requiredAdmin();
        if (userMapper.findById(userId) == null || orgMapper.findById(request.getOrgId(), principal.getTenantId()) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户或组织不存在");
        }
        if (!"HQ_ADMIN".equals(principal.getRole()) && !principal.getOrgId().equals(request.getOrgId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        UserOrgRole role = userOrgRoleMapper.findByUserOrgRole(principal.getTenantId(), userId,
                request.getOrgId(), request.getRole());
        if (role == null) {
            role = new UserOrgRole();
            role.setTenantId(principal.getTenantId());
            role.setUserId(userId);
            role.setOrgId(request.getOrgId());
            role.setRole(request.getRole());
            role.setDataScope(request.getDataScope());
            userOrgRoleMapper.insert(role);
        } else {
            userOrgRoleMapper.updateRole(role.getId(), principal.getTenantId(), request.getRole(), request.getDataScope());
        }
    }
}
