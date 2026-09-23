package com.xiaoa.admin.controller;

import com.xiaoa.admin.dto.CreateStoreRequest;
import com.xiaoa.admin.dto.UpdateStoreParentRequest;
import com.xiaoa.admin.model.StoreAccountSummary;
import com.xiaoa.admin.service.StoreAdminService;
import com.xiaoa.common.api.Result;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/admin/stores")
@Validated
public class StoreAdminController {

    private final StoreAdminService storeService;

    public StoreAdminController(StoreAdminService storeService) {
        this.storeService = storeService;
    }

    @GetMapping
    public Result<List<StoreAccountSummary>> list() {
        return Result.success(storeService.list());
    }

    @PostMapping
    public Result<StoreAccountSummary> create(@Valid @RequestBody CreateStoreRequest request) {
        return Result.success(storeService.create(request));
    }

    @PatchMapping("/{storeId}/parent")
    public Result<Void> updateParent(@PathVariable Long storeId,
                                     @Valid @RequestBody UpdateStoreParentRequest request) {
        storeService.updateParent(storeId, request);
        return Result.success();
    }
}
