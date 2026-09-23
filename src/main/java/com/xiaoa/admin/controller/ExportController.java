package com.xiaoa.admin.controller;

import com.xiaoa.admin.dto.CreateExportRequest;
import com.xiaoa.admin.model.ExportTask;
import com.xiaoa.admin.service.ExportService;
import com.xiaoa.common.api.Result;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/admin/exports")
@Validated
public class ExportController {

    private final ExportService exportService;

    public ExportController(ExportService exportService) {
        this.exportService = exportService;
    }

    @PostMapping
    public Result<ExportTask> create(@Valid @RequestBody CreateExportRequest request) {
        return Result.success(exportService.create(request));
    }

    @GetMapping
    public Result<List<ExportTask>> list() {
        return Result.success(exportService.list());
    }

    @GetMapping("/{id}")
    public Result<ExportTask> get(@PathVariable Long id) {
        return Result.success(exportService.get(id));
    }
}
