package com.xiaoa.asset.controller;

import com.xiaoa.asset.dto.ContentPackageRequest;
import com.xiaoa.asset.model.ContentPackage;
import com.xiaoa.asset.service.ContentPackageService;
import com.xiaoa.common.api.PageResult;
import com.xiaoa.common.api.Result;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * 内容包配置：创建（模板强校验、营销日不能早于今天）、列表、撤销（ACTIVE→CANCELED）。
 */
@RestController
@RequestMapping("/api/admin/content-packages")
@Validated
public class ContentPackageController {

    private final ContentPackageService contentPackageService;

    public ContentPackageController(ContentPackageService contentPackageService) {
        this.contentPackageService = contentPackageService;
    }

    @PostMapping
    public Result<ContentPackage> create(@Valid @RequestBody ContentPackageRequest request) {
        return Result.success(contentPackageService.create(request));
    }

    @GetMapping
    public Result<PageResult<ContentPackage>> page(@RequestParam(value = "status", required = false) Integer status,
                                                   @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
                                                   @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        return Result.success(contentPackageService.page(status, pageNo, pageSize));
    }

    @DeleteMapping("/{id}")
    public Result<Void> cancel(@PathVariable Long id) {
        contentPackageService.cancel(id);
        return Result.success();
    }
}
