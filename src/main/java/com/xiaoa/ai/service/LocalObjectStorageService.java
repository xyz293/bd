package com.xiaoa.ai.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Component
public class LocalObjectStorageService implements ObjectStorageService {

    private final String baseDir;

    public LocalObjectStorageService(@Value("${xiaoa.storage.local-dir:./data/storage}") String baseDir) {
        this.baseDir = baseDir == null || baseDir.trim().isEmpty() ? "./data/storage" : baseDir.trim();
    }

    @Override
    public String transfer(Long tenantId, Long workId, String sourceUrl) {
        if (sourceUrl == null || sourceUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("模型未返回成品地址");
        }
        return "local://ai/" + tenantId + "/" + workId + "/" + UUID.randomUUID();
    }

    @Override
    public String save(Long tenantId, Long storeId, String originalFilename, InputStream inputStream, long size) {
        String ext = extOf(originalFilename);
        Path dir = storeId == null
                ? Paths.get(baseDir, "asset", String.valueOf(tenantId), "brand")
                : Paths.get(baseDir, "asset", String.valueOf(tenantId), "store", String.valueOf(storeId));
        try {
            Files.createDirectories(dir);
            Path target = dir.resolve(UUID.randomUUID() + ext);
            Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            return storeId == null
                    ? "local://asset/" + tenantId + "/brand/" + target.getFileName()
                    : "local://asset/" + tenantId + "/store/" + storeId + "/" + target.getFileName();
        } catch (Exception exception) {
            throw new IllegalStateException("素材文件保存失败", exception);
        }
    }

    private String extOf(String originalFilename) {
        String name = originalFilename == null ? "" : originalFilename.trim().toLowerCase();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot);
    }
}
