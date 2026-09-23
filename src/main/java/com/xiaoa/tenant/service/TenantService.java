package com.xiaoa.tenant.service;

import com.xiaoa.common.exception.BusinessException;
import com.xiaoa.common.exception.ErrorCode;
import com.xiaoa.tenant.dto.OpenTenantRequest;
import com.xiaoa.tenant.dto.OpenTenantResponse;
import com.xiaoa.tenant.mapper.OrgMapper;
import com.xiaoa.tenant.mapper.TenantMapper;
import com.xiaoa.tenant.mapper.UserMapper;
import com.xiaoa.tenant.mapper.UserOrgRoleMapper;
import com.xiaoa.tenant.model.Org;
import com.xiaoa.tenant.model.Tenant;
import com.xiaoa.tenant.model.UserAccount;
import com.xiaoa.tenant.model.UserOrgRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class TenantService {

    private final TenantMapper tenantMapper;
    private final OrgMapper orgMapper;
    private final UserMapper userMapper;
    private final UserOrgRoleMapper userOrgRoleMapper;

    public TenantService(TenantMapper tenantMapper, OrgMapper orgMapper, UserMapper userMapper,
                         UserOrgRoleMapper userOrgRoleMapper) {
        this.tenantMapper = tenantMapper;
        this.orgMapper = orgMapper;
        this.userMapper = userMapper;
        this.userOrgRoleMapper = userOrgRoleMapper;
    }

    @Transactional
    public OpenTenantResponse open(OpenTenantRequest request) {
        if (userMapper.findByPhone(request.getAdminPhone()) != null) {
            throw new BusinessException(ErrorCode.DUPLICATE, "管理员手机号已存在");
        }
        Tenant tenant = new Tenant();
        tenant.setName(request.getName());
        tenant.setType(request.getType());
        tenant.setIndustry(request.getIndustry());
        tenant.setAssetPackageId(request.getAssetPackageId());
        tenant.setStatus(1);
        tenant.setExpireAt(request.getExpireAt());
        tenantMapper.insert(tenant);

        Org brand = new Org();
        brand.setTenantId(tenant.getId());
        brand.setParentId(0L);
        brand.setType(1);
        brand.setName(request.getName());
        orgMapper.insert(brand);

        UserAccount user = new UserAccount();
        user.setPhone(request.getAdminPhone());
        user.setOpenid(request.getAdminOpenid());
        user.setNickname(request.getAdminNickname());
        userMapper.insert(user);

        UserOrgRole role = new UserOrgRole();
        role.setTenantId(tenant.getId());
        role.setUserId(user.getId());
        role.setOrgId(brand.getId());
        role.setRole("HQ_ADMIN");
        role.setDataScope(1);
        userOrgRoleMapper.insert(role);
        return new OpenTenantResponse(tenant.getId(), brand.getId(), user.getId());
    }

    public Tenant get(Long tenantId) {
        Tenant tenant = tenantMapper.findById(tenantId);
        if (tenant == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "租户不存在");
        }
        return tenant;
    }

    public void ensureUsable(Tenant tenant) {
        if (tenant.getStatus() != null && tenant.getStatus() == 2) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "租户已停用");
        }
        if (tenant.getExpireAt() != null && tenant.getExpireAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.TENANT_EXPIRED);
        }
    }
}
