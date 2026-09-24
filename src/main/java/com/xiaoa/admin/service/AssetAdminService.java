package com.xiaoa.admin.service;

import com.xiaoa.admin.dto.ReviewAssetRequest;
import com.xiaoa.admin.mapper.AssetAdminMapper;
import com.xiaoa.admin.model.AssetAdminItem;
import com.xiaoa.common.auth.AdminPermissionService;
import com.xiaoa.common.exception.BusinessException;
import com.xiaoa.common.exception.ErrorCode;
import com.xiaoa.task.mapper.MessageMapper;
import com.xiaoa.task.model.Message;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AssetAdminService {

    private final AssetAdminMapper assetMapper;
    private final AdminPermissionService permissionService;
    private final MessageMapper messageMapper;

    public AssetAdminService(AssetAdminMapper assetMapper, AdminPermissionService permissionService,
                             MessageMapper messageMapper) {
        this.assetMapper = assetMapper;
        this.permissionService = permissionService;
        this.messageMapper = messageMapper;
    }

    public List<AssetAdminItem> list() {
        return assetMapper.findVisible(permissionService.requiredRead().getTenantId());
    }

    /**
     * 推优复核：2 通过（scope 升级全品牌可见）；3 驳回并消息通知推荐人。
     */
    @Transactional
    public void review(Long id, ReviewAssetRequest request) {
        permissionService.requireHeadquarters();
        Long tenantId = permissionService.required().getTenantId();
        AssetAdminItem asset = assetMapper.findVisibleById(tenantId, id);
        if (asset == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "素材不存在");
        }
        if (assetMapper.review(tenantId, id, request.getRecommendStatus()) != 1) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "素材不存在");
        }
        if (request.getRecommendStatus() != null && request.getRecommendStatus() == 3
                && asset.getOwnerId() != null && asset.getOwnerId() > 0) {
            Message message = new Message();
            message.setTenantId(tenantId);
            message.setUserId(asset.getOwnerId());
            message.setType("ASSET_RECOMMEND");
            message.setTitle("素材推优被驳回");
            message.setContent("你推优的素材「" + asset.getName() + "」未通过审核");
            messageMapper.insert(message);
        }
    }
}
