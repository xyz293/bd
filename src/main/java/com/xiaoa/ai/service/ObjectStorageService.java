package com.xiaoa.ai.service;

import java.io.InputStream;

public interface ObjectStorageService {

    String transfer(Long tenantId, Long workId, String sourceUrl);

    /**
     * 保存上传文件，返回可访问地址。先传文件后落库，落库失败容忍孤儿文件。
     * storeId 为空表示品牌层目录，否则为本店层目录。
     */
    String save(Long tenantId, Long storeId, String originalFilename, InputStream inputStream, long size);
}
