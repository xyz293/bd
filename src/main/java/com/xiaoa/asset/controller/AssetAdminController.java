package com.xiaoa.asset.controller;

import com.xiaoa.asset.dto.AssetUpdateRequest;
import com.xiaoa.asset.dto.ReviewAssetRequest;
import com.xiaoa.asset.model.Asset;
import com.xiaoa.asset.service.AssetService;
import com.xiaoa.common.api.PageResult;
import com.xiaoa.common.api.Result;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;

/**
 * 管理端素材：品牌层上传、列表（分类/状态/推优Tab）、编辑、软删、推优审核。
 */
@RestController
@RequestMapping("/api/admin/assets")
@Validated
public class AssetAdminController {

    private final AssetService assetService;

    public AssetAdminController(AssetService assetService) {
        this.assetService = assetService;
    }

    /** 品牌素材上传（HQ_ADMIN） */
    @PostMapping
    public Result<Asset> upload(@RequestParam("file") MultipartFile file,
                                @RequestParam(value = "name", required = false) String name,
                                @RequestParam(value = "category", required = false) String category) {
        return Result.success(assetService.uploadBrand(file, name, category));
    }

    /** 素材列表：scope/status/category 过滤，status=PENDING_REVIEW 即推优 Tab */
    @GetMapping
    public Result<PageResult<Asset>> page(@RequestParam(value = "scope", required = false) String scope,
                                          @RequestParam(value = "status", required = false) String status,
                                          @RequestParam(value = "category", required = false) String category,
                                          @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
                                          @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        return Result.success(assetService.adminPage(scope, status, category, pageNo, pageSize));
    }

    /** 编辑名称/分类（行业包素材只读；品牌层总部、本店层店长） */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody AssetUpdateRequest request) {
        assetService.update(id, request);
        return Result.success();
    }

    /** 软删 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        assetService.delete(id);
        return Result.success();
    }

    /** 推优审核：pass 升层 BRAND，reject 通知上传人 */
    @PutMapping("/{id}/review")
    public Result<Void> review(@PathVariable Long id, @Valid @RequestBody ReviewAssetRequest request) {
        assetService.review(id, request);
        return Result.success();
    }
}
