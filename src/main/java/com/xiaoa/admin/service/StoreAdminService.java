package com.xiaoa.admin.service;

import com.xiaoa.admin.dto.CreateStoreRequest;
import com.xiaoa.admin.dto.UpdateStoreParentRequest;
import com.xiaoa.admin.mapper.AdminStoreMapper;
import com.xiaoa.admin.model.StoreAccountSummary;
import com.xiaoa.common.auth.AdminPermissionService;
import com.xiaoa.common.auth.AuthPrincipal;
import com.xiaoa.common.exception.BusinessException;
import com.xiaoa.common.exception.ErrorCode;
import com.xiaoa.quota.service.QuotaService;
import com.xiaoa.tenant.mapper.OrgMapper;
import com.xiaoa.tenant.model.Org;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class StoreAdminService {

    private final AdminStoreMapper storeMapper;
    private final OrgMapper orgMapper;
    private final AdminPermissionService permissionService;
    private final QuotaService quotaService;

    public StoreAdminService(AdminStoreMapper storeMapper, OrgMapper orgMapper,
                             AdminPermissionService permissionService, QuotaService quotaService) {
        this.storeMapper = storeMapper;
        this.orgMapper = orgMapper;
        this.permissionService = permissionService;
        this.quotaService = quotaService;
    }

    public List<StoreAccountSummary> list() {
        AuthPrincipal principal = permissionService.requiredRead();
        List<StoreAccountSummary> result = new ArrayList<>();
        for (Org store : storeMapper.findStores(principal.getTenantId())) {
            if (!canViewStore(principal, store)) {
                continue;
            }
            result.add(new StoreAccountSummary(store, storeMapper.countMembers(principal.getTenantId(), store.getId()),
                    storeMapper.findOwner(principal.getTenantId(), store.getId())));
        }
        return result;
    }

    @Transactional
    public StoreAccountSummary create(CreateStoreRequest request) {
        AuthPrincipal principal = permissionService.requiredWrite();
        Long parentId = request.getParentId() == null ? principal.getOrgId() : request.getParentId();
        if ("REGION_ADMIN".equals(principal.getRole()) && !principal.getOrgId().equals(parentId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "区域管理员只能在本区域下创建门店");
        }
        Org parent = orgMapper.findById(parentId, principal.getTenantId());
        if (parent == null || parent.getType() == null || (parent.getType() != 1 && parent.getType() != 2)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "门店上级必须是品牌或区域节点");
        }
        Org store = new Org();
        store.setTenantId(principal.getTenantId());
        store.setParentId(parentId);
        store.setType(3);
        store.setName(request.getName());
        orgMapper.insert(store);
        quotaService.ensureStoreAccount(store.getId());
        return new StoreAccountSummary(store, 0, null);
    }

    @Transactional
    public void updateParent(Long storeId, UpdateStoreParentRequest request) {
        AuthPrincipal principal = permissionService.requiredWrite();
        Org store = storeMapper.findStore(principal.getTenantId(), storeId);
        Org parent = orgMapper.findById(request.getParentId(), principal.getTenantId());
        if (store == null || parent == null || parent.getType() == 3) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "门店或归属区域不存在");
        }
        if ("REGION_ADMIN".equals(principal.getRole()) && !parent.getId().equals(principal.getOrgId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能调整到本区域");
        }
        orgMapper.updateParent(storeId, principal.getTenantId(), parent.getId());
    }

    private boolean canViewStore(AuthPrincipal principal, Org store) {
        return "HQ_ADMIN".equals(principal.getRole()) || "VIEWER".equals(principal.getRole())
                || ("REGION_ADMIN".equals(principal.getRole()) && store.getParentId().equals(principal.getOrgId()));
    }
}
