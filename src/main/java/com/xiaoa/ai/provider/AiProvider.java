package com.xiaoa.ai.provider;

import com.xiaoa.ai.model.AiRequest;
import com.xiaoa.ai.model.AiResult;

public interface AiProvider {

    AiResult generateImage(AiRequest request);

    String submitVideoTask(AiRequest request);

    AiResult queryVideoTask(String externalTaskId);

    /**
     * 图文成套文案生成；返回 null/空表示不支持或无内容，作品 caption 留空。
     */
    default String generateCaption(AiRequest request) {
        return null;
    }
}
