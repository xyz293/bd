package com.xiaoa.tenant.service;

import com.xiaoa.common.auth.AuthPrincipal;
import com.xiaoa.common.exception.BusinessException;
import com.xiaoa.common.exception.ErrorCode;
import com.xiaoa.tenant.dto.CreateInviteRequest;
import com.xiaoa.tenant.dto.InviteResponse;
import com.xiaoa.tenant.mapper.InviteCodeMapper;
import com.xiaoa.tenant.mapper.OrgMapper;
import com.xiaoa.tenant.model.InviteCode;
import com.xiaoa.tenant.model.Org;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class InviteService {

    private final InviteCodeMapper inviteCodeMapper;
    private final OrgMapper orgMapper;
    private final PermissionService permissionService;

    public InviteService(InviteCodeMapper inviteCodeMapper, OrgMapper orgMapper,
                         PermissionService permissionService) {
        this.inviteCodeMapper = inviteCodeMapper;
        this.orgMapper = orgMapper;
        this.permissionService = permissionService;
    }

    @Transactional
    public InviteResponse create(CreateInviteRequest request) {
        AuthPrincipal principal = permissionService.requiredAdmin();
        Org store = orgMapper.findById(request.getStoreId(), principal.getTenantId());
        if (store == null || store.getType() != 3) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "门店不存在");
        }
        if (!"HQ_ADMIN".equals(principal.getRole()) && !principal.getOrgId().equals(store.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        String code = generateCode();
        InviteCode inviteCode = new InviteCode();
        inviteCode.setTenantId(principal.getTenantId());
        inviteCode.setStoreId(request.getStoreId());
        inviteCode.setCode(code);
        inviteCode.setRole("STAFF");
        inviteCode.setExpireAt(request.getExpireAt());
        inviteCode.setCreatedBy(principal.getUserId());
        inviteCodeMapper.insert(inviteCode);
        return new InviteResponse(inviteCode.getId(), inviteCode.getStoreId(), inviteCode.getCode(),
                inviteCode.getRole(), inviteCode.getExpireAt());
    }

    public InviteCode validate(String code) {
        InviteCode inviteCode = inviteCodeMapper.findByCode(code);
        if (inviteCode == null) {
            throw new BusinessException(ErrorCode.INVITE_INVALID);
        }
        if (inviteCode.getUsed() != null && inviteCode.getUsed() == 1) {
            throw new BusinessException(ErrorCode.INVITE_USED);
        }
        if (inviteCode.getExpireAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.INVITE_EXPIRED);
        }
        return inviteCode;
    }

    @Transactional
    public InviteCode consume(String code) {
        InviteCode inviteCode = validate(code);
        if (inviteCodeMapper.consume(inviteCode.getId(), LocalDateTime.now()) != 1) {
            throw new BusinessException(ErrorCode.INVITE_USED);
        }
        return inviteCode;
    }

    private String generateCode() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }
}
