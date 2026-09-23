package com.xiaoa.task.controller;

import com.xiaoa.common.api.Result;
import com.xiaoa.task.dto.PublishRecordRequest;
import com.xiaoa.task.model.PublishRecord;
import com.xiaoa.task.service.PublishRecordService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/publish-record")
@Validated
public class PublishRecordController {

    private final PublishRecordService publishRecordService;

    public PublishRecordController(PublishRecordService publishRecordService) {
        this.publishRecordService = publishRecordService;
    }

    @PostMapping
    public Result<PublishRecord> publish(@Valid @RequestBody PublishRecordRequest request) {
        return Result.success(publishRecordService.publish(request));
    }
}
