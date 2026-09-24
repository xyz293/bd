package com.xiaoa.asset.dto;

import javax.validation.constraints.NotNull;

public class RecommendAssetRequest {

    @NotNull(message = "素材ID不能为空")
    private Long assetId;

    public Long getAssetId() { return assetId; }
    public void setAssetId(Long assetId) { this.assetId = assetId; }
}
