package com.xiaoa.asset.service;

import com.xiaoa.asset.mapper.AssetMapper;
import com.xiaoa.asset.model.Asset;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 三层可见性即下发：PLATFORM（租户挂载/同行业行业包）∪ BRAND（本租户）∪ STORE（本店），仅 APPROVED 可见。
 */
@Service
public class AssetVisibilityService {

    private static final int MAX_VISIBLE = 200;

    private final AssetMapper assetMapper;

    public AssetVisibilityService(AssetMapper assetMapper) {
        this.assetMapper = assetMapper;
    }

    public List<Asset> visibleFor(Long tenantId, Long storeId, String category) {
        return assetMapper.selectVisible(tenantId, storeId, category, MAX_VISIBLE);
    }

    public Asset findVisible(Long tenantId, Long storeId, Long assetId) {
        return assetMapper.findVisibleById(tenantId, storeId, assetId);
    }
}
