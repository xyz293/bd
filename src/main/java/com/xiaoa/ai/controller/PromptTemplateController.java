package com.xiaoa.ai.controller;

import com.xiaoa.ai.dto.PromptTemplateRequest;
import com.xiaoa.ai.model.PromptTemplate;
import com.xiaoa.ai.service.PromptTemplateService;
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
@RequestMapping("/api/admin/prompt-template")
@Validated
public class PromptTemplateController {

    private final PromptTemplateService promptTemplateService;

    public PromptTemplateController(PromptTemplateService promptTemplateService) {
        this.promptTemplateService = promptTemplateService;
    }

    @GetMapping
    public Result<List<PromptTemplate>> list() {
        return Result.success(promptTemplateService.list());
    }

    @PutMapping
    public Result<PromptTemplate> save(@Valid @RequestBody PromptTemplateRequest request) {
        return Result.success(promptTemplateService.save(request));
    }

    @PutMapping("/{id}/rollback")
    public Result<Void> rollback(@PathVariable Long id) {
        promptTemplateService.rollback(id);
        return Result.success();
    }
}
