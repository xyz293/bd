package com.xiaoa.admin.controller;

import com.xiaoa.admin.dto.RecommendAssetRequest;
import com.xiaoa.admin.service.AssetRecommendService;
import com.xiaoa.common.api.Result;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/assets")
@Validated
public class AssetRecommendController {

    private final AssetRecommendService assetRecommendService;

    public AssetRecommendController(AssetRecommendService assetRecommendService) {
        this.assetRecommendService = assetRecommendService;
    }

    @PostMapping("/recommend")
    public Result<Void> recommend(@Valid @RequestBody RecommendAssetRequest request) {
        assetRecommendService.recommend(request);
        return Result.success();
    }
}
