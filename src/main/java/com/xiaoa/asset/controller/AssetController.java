package com.xiaoa.asset.controller;

import com.xiaoa.asset.dto.RecommendAssetRequest;
import com.xiaoa.asset.model.Asset;
import com.xiaoa.asset.service.AssetService;
import com.xiaoa.common.api.Result;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;
import java.util.List;

/**
 * 门店端素材：本店上传、三层可见性查询、推优。
 */
@RestController
@RequestMapping("/api/assets")
@Validated
public class AssetController {

    private final AssetService assetService;

    public AssetController(AssetService assetService) {
        this.assetService = assetService;
    }

    /** 本店素材上传（STAFF/OWNER），即传即用 */
    @PostMapping
    public Result<Asset> upload(@RequestParam("file") MultipartFile file,
                                @RequestParam(value = "name", required = false) String name,
                                @RequestParam(value = "category", required = false) String category) {
        return Result.success(assetService.uploadStore(file, name, category));
    }

    /** 三层可见性查询：PLATFORM(挂载行业包) ∪ BRAND(本租户) ∪ STORE(本店) */
    @GetMapping
    public Result<List<Asset>> visible(@RequestParam(value = "category", required = false) String category) {
        return Result.success(assetService.visible(category));
    }

    /** 推优本店素材 */
    @PostMapping("/recommend")
    public Result<Void> recommend(@Valid @RequestBody RecommendAssetRequest request) {
        assetService.recommend(request.getAssetId());
        return Result.success();
    }
}
