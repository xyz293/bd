package com.xiaoa.ai.provider;

import com.xiaoa.ai.model.AiRequest;
import com.xiaoa.ai.model.AiResult;

public interface AiProvider {

    AiResult generateImage(AiRequest request);

    String submitVideoTask(AiRequest request);

    AiResult queryVideoTask(String externalTaskId);
}
