package com.xiaoa.admin.controller;

import com.xiaoa.admin.dto.CreateContentPackageRequest;
import com.xiaoa.admin.model.ContentPackage;
import com.xiaoa.admin.service.ContentPackageService;
import com.xiaoa.common.api.Result;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/admin/content-packages")
@Validated
public class ContentPackageController {

    private final ContentPackageService packageService;

    public ContentPackageController(ContentPackageService packageService) {
        this.packageService = packageService;
    }

    @GetMapping
    public Result<List<ContentPackage>> list() {
        return Result.success(packageService.list());
    }

    @PostMapping
    public Result<ContentPackage> create(@Valid @RequestBody CreateContentPackageRequest request) {
        return Result.success(packageService.create(request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> disable(@PathVariable Long id) {
        packageService.disable(id);
        return Result.success();
    }
}
