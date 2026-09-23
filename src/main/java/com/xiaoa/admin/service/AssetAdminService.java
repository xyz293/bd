package com.xiaoa.admin.service;

import com.xiaoa.admin.dto.ReviewAssetRequest;
import com.xiaoa.admin.mapper.AssetAdminMapper;
import com.xiaoa.admin.model.AssetAdminItem;
import com.xiaoa.common.auth.AdminPermissionService;
import com.xiaoa.common.exception.BusinessException;
import com.xiaoa.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AssetAdminService {

    private final AssetAdminMapper assetMapper;
    private final AdminPermissionService permissionService;

    public AssetAdminService(AssetAdminMapper assetMapper, AdminPermissionService permissionService) {
        this.assetMapper = assetMapper;
        this.permissionService = permissionService;
    }

    public List<AssetAdminItem> list() {
        return assetMapper.findVisible(permissionService.requiredRead().getTenantId());
    }

    @Transactional
    public void review(Long id, ReviewAssetRequest request) {
        permissionService.requireHeadquarters();
        Long tenantId = permissionService.required().getTenantId();
        if (assetMapper.review(tenantId, id, request.getRecommendStatus()) != 1) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "素材不存在");
        }
    }
}
