package com.xiaoa.admin.controller;

import com.xiaoa.admin.dto.CreateStyleRequest;
import com.xiaoa.admin.dto.UpdateStyleRequest;
import com.xiaoa.admin.model.StyleOption;
import com.xiaoa.admin.service.StyleService;
import com.xiaoa.common.api.Result;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/admin/styles")
@Validated
public class StyleController {

    private final StyleService styleService;

    public StyleController(StyleService styleService) {
        this.styleService = styleService;
    }

    @GetMapping
    public Result<List<StyleOption>> visible() {
        return Result.success(styleService.visible());
    }

    @PostMapping
    public Result<StyleOption> create(@Valid @RequestBody CreateStyleRequest request) {
        return Result.success(styleService.create(request));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody UpdateStyleRequest request) {
        styleService.update(id, request);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        styleService.delete(id);
        return Result.success();
    }
}
