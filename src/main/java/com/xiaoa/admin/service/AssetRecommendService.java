package com.xiaoa.admin.service;

import com.xiaoa.admin.dto.RecommendAssetRequest;
import com.xiaoa.admin.mapper.AssetAdminMapper;
import com.xiaoa.admin.model.AssetAdminItem;
import com.xiaoa.common.auth.AuthContext;
import com.xiaoa.common.auth.AuthPrincipal;
import com.xiaoa.common.exception.BusinessException;
import com.xiaoa.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 素材推优：员工把可见素材提交进品牌推优流程（recommend_status=1 并记录推荐人）。
 * 总部通过 PUT /api/admin/assets/{id}/review 复核：2 通过（升级全品牌可见）、3 驳回（消息通知推荐人）。
 */
@Service
public class AssetRecommendService {

    private final AssetAdminMapper assetMapper;

    public AssetRecommendService(AssetAdminMapper assetMapper) {
        this.assetMapper = assetMapper;
    }

    @Transactional
    public void recommend(RecommendAssetRequest request) {
        AuthPrincipal principal = AuthContext.required();
        AssetAdminItem asset = assetMapper.findVisibleById(principal.getTenantId(), request.getAssetId());
        if (asset == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "素材不存在或不可见");
        }
        if (asset.getRecommendStatus() != null
                && (asset.getRecommendStatus() == 1 || asset.getRecommendStatus() == 2)) {
            throw new BusinessException(ErrorCode.DUPLICATE, "素材已在推优流程中");
        }
        if (assetMapper.markRecommended(principal.getTenantId(), request.getAssetId(), principal.getUserId()) != 1) {
            throw new BusinessException(ErrorCode.DUPLICATE, "素材已在推优流程中");
        }
    }
}
