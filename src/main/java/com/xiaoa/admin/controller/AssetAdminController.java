package com.xiaoa.admin.controller;

import com.xiaoa.admin.dto.ReviewAssetRequest;
import com.xiaoa.admin.model.AssetAdminItem;
import com.xiaoa.admin.service.AssetAdminService;
import com.xiaoa.common.api.Result;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/admin/assets")
@Validated
public class AssetAdminController {

    private final AssetAdminService assetService;

    public AssetAdminController(AssetAdminService assetService) {
        this.assetService = assetService;
    }

    @GetMapping
    public Result<List<AssetAdminItem>> list() {
        return Result.success(assetService.list());
    }

    @PutMapping("/{id}/review")
    public Result<Void> review(@PathVariable Long id, @Valid @RequestBody ReviewAssetRequest request) {
        assetService.review(id, request);
        return Result.success();
    }
}
