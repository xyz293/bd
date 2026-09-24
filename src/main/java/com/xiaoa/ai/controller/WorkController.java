package com.xiaoa.ai.controller;

import com.xiaoa.ai.dto.GenerateRequest;
import com.xiaoa.ai.dto.UpdateWorkCaptionRequest;
import com.xiaoa.ai.model.Work;
import com.xiaoa.ai.service.AiGatewayService;
import com.xiaoa.ai.service.WorkService;
import com.xiaoa.common.api.Result;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/work")
@Validated
public class WorkController {

    private final AiGatewayService aiGatewayService;
    private final WorkService workService;

    public WorkController(AiGatewayService aiGatewayService, WorkService workService) {
        this.aiGatewayService = aiGatewayService;
        this.workService = workService;
    }

    @PostMapping("/generate")
    public Result<Work> generate(@Valid @RequestBody GenerateRequest request) {
        return Result.success(aiGatewayService.generate(request));
    }

    @GetMapping("/{id}")
    public Result<Work> get(@PathVariable Long id) {
        return Result.success(workService.detail(id));
    }

    @PutMapping("/{id}/caption")
    public Result<Void> updateCaption(@PathVariable Long id, @Valid @RequestBody UpdateWorkCaptionRequest request) {
        workService.updateCaption(id, request.getCaption());
        return Result.success();
    }

    @PostMapping("/{id}/regenerate")
    public Result<Work> regenerate(@PathVariable Long id) {
        return Result.success(workService.regenerate(id));
    }
}
