package com.xiaoa.ai.service;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class LocalObjectStorageService implements ObjectStorageService {

    @Override
    public String transfer(Long tenantId, Long workId, String sourceUrl) {
        if (sourceUrl == null || sourceUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("模型未返回成品地址");
        }
        return "local://ai/" + tenantId + "/" + workId + "/" + UUID.randomUUID();
    }
}
