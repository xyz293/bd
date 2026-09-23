package com.xiaoa.tenant.service;

import com.xiaoa.common.auth.AuthPrincipal;
import com.xiaoa.common.exception.BusinessException;
import com.xiaoa.common.exception.ErrorCode;
import com.xiaoa.tenant.dto.CreateOrgRequest;
import com.xiaoa.tenant.mapper.OrgMapper;
import com.xiaoa.tenant.model.Org;
import com.xiaoa.tenant.model.OrgTreeNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class OrgService {

    private final OrgMapper orgMapper;
    private final PermissionService permissionService;

    public OrgService(OrgMapper orgMapper, PermissionService permissionService) {
        this.orgMapper = orgMapper;
        this.permissionService = permissionService;
    }

    @Transactional
    public Org create(CreateOrgRequest request) {
        AuthPrincipal principal = permissionService.requiredAdmin();
        long parentId = request.getParentId() == null ? 0L : request.getParentId();
        if (parentId != 0L && orgMapper.findById(parentId, principal.getTenantId()) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "上级组织不存在");
        }
        if ("OWNER".equals(principal.getRole()) && request.getType() != 3) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "店长只能创建门店节点");
        }
        Org org = new Org();
        org.setTenantId(principal.getTenantId());
        org.setParentId(parentId);
        org.setType(request.getType());
        org.setName(request.getName());
        orgMapper.insert(org);
        return org;
    }

    public List<OrgTreeNode> tree() {
        AuthPrincipal principal = permissionService.required();
        List<Org> orgs = orgMapper.findByTenantId(principal.getTenantId());
        Map<Long, OrgTreeNode> nodes = new HashMap<>();
        for (Org org : orgs) {
            nodes.put(org.getId(), new OrgTreeNode(org));
        }
        for (Org org : orgs) {
            if (org.getParentId() != null && org.getParentId() != 0L) {
                OrgTreeNode parent = nodes.get(org.getParentId());
                if (parent != null) {
                    parent.getChildren().add(nodes.get(org.getId()));
                }
            }
        }
        java.util.ArrayList<OrgTreeNode> roots = new java.util.ArrayList<>();
        for (Org org : orgs) {
            if (org.getParentId() == null || org.getParentId() == 0L) {
                roots.add(nodes.get(org.getId()));
            }
        }
        return roots;
    }

    public void rename(Long orgId, String name) {
        AuthPrincipal principal = permissionService.requiredAdmin();
        Org org = orgMapper.findById(orgId, principal.getTenantId());
        if (org == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "组织不存在");
        }
        if (!permissionService.canManageOrg(orgId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        orgMapper.updateName(orgId, principal.getTenantId(), name);
    }
}
