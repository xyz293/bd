package com.xiaoa.ai.provider;

import com.xiaoa.ai.model.AiRequest;
import com.xiaoa.ai.model.AiResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@ConditionalOnProperty(name = "xiaoa.ai.provider.type", havingValue = "demo", matchIfMissing = true)
public class DemoAiProvider implements AiProvider {

    @Override
    public AiResult generateImage(AiRequest request) {
        if (request.getPrompt() == null || request.getPrompt().trim().isEmpty()) {
            return AiResult.failure("CONTENT_EMPTY", "生成提示词为空");
        }
        return AiResult.success("demo://image/" + UUID.randomUUID());
    }

    @Override
    public String submitVideoTask(AiRequest request) {
        if (request.getPrompt() == null || request.getPrompt().trim().isEmpty()) {
            throw new IllegalArgumentException("生成提示词为空");
        }
        return "demo-video-" + UUID.randomUUID();
    }

    @Override
    public AiResult queryVideoTask(String externalTaskId) {
        return AiResult.success("demo://video/" + externalTaskId);
    }
}
