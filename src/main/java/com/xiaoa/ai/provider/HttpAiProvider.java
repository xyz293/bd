package com.xiaoa.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.xiaoa.ai.model.AiRequest;
import com.xiaoa.ai.model.AiResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "xiaoa.ai.provider.type", havingValue = "http")
public class HttpAiProvider implements AiProvider {

    private final RestTemplate restTemplate;
    private final String imageUrl;
    private final String videoSubmitUrl;
    private final String videoQueryUrl;
    private final String apiKey;

    public HttpAiProvider(@Value("${xiaoa.ai.provider.image-url:}") String imageUrl,
                          @Value("${xiaoa.ai.provider.video-submit-url:}") String videoSubmitUrl,
                          @Value("${xiaoa.ai.provider.video-query-url:}") String videoQueryUrl,
                          @Value("${xiaoa.ai.provider.api-key:}") String apiKey,
                          @Value("${xiaoa.ai.provider.timeout-ms:60000}") int timeoutMs) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeoutMs);
        requestFactory.setReadTimeout(timeoutMs);
        this.restTemplate = new RestTemplate(requestFactory);
        this.imageUrl = imageUrl == null ? "" : imageUrl;
        this.videoSubmitUrl = videoSubmitUrl == null ? "" : videoSubmitUrl;
        this.videoQueryUrl = videoQueryUrl == null ? "" : videoQueryUrl;
        this.apiKey = apiKey == null ? "" : apiKey;
    }

    @Override
    public AiResult generateImage(AiRequest request) {
        JsonNode response = post(imageUrl, request);
        return result(response);
    }

    @Override
    public String submitVideoTask(AiRequest request) {
        JsonNode response = post(videoSubmitUrl, request);
        String taskId = text(response, "externalTaskId");
        return taskId == null ? text(response, "taskId") : taskId;
    }

    @Override
    public AiResult queryVideoTask(String externalTaskId) {
        if (externalTaskId == null || externalTaskId.trim().isEmpty()) {
            return AiResult.failure("MODEL_CONFIG_ERROR", "视频任务ID为空");
        }
        if (videoQueryUrl.trim().isEmpty()) {
            return AiResult.failure("MODEL_CONFIG_ERROR", "视频查询地址未配置");
        }
        String taskId = externalTaskId.trim();
        String url = videoQueryUrl.contains("{taskId}")
                ? videoQueryUrl.replace("{taskId}", taskId)
                : videoQueryUrl + (videoQueryUrl.contains("?") ? "&" : "?") + "taskId=" + taskId;
        try {
            String requestUrl = java.util.Objects.requireNonNull(url);
            JsonNode response = restTemplate.exchange(requestUrl, org.springframework.http.HttpMethod.GET,
                    new HttpEntity<Object>(null, headers()), JsonNode.class).getBody();
            return result(response);
        } catch (RuntimeException exception) {
            return AiResult.failure("MODEL_TIMEOUT", safeMessage(exception));
        }
    }

    private JsonNode post(String url, AiRequest request) {
        if (url == null || url.trim().isEmpty()) {
            throw new IllegalStateException("AI模型地址未配置");
        }
        Map<String, Object> body = new HashMap<>();
        body.put("prompt", request.getPrompt());
        body.put("styleName", request.getStyleName());
        body.put("refImageUrls", request.getRefImageUrls());
        try {
            return restTemplate.postForEntity(url, new HttpEntity<>(body, headers()), JsonNode.class).getBody();
        } catch (RuntimeException exception) {
            throw new IllegalStateException(safeMessage(exception), exception);
        }
    }

    private AiResult result(JsonNode response) {
        if (response == null || response.isNull()) {
            return AiResult.failure("CONTENT_EMPTY", "模型返回为空");
        }
        int status = response.path("status").asInt(2);
        String contentUrl = text(response, "contentUrl");
        if (contentUrl == null) {
            contentUrl = text(response, "url");
        }
        if (status == 0) {
            return AiResult.processing();
        }
        if (status == 1 && contentUrl != null) {
            return AiResult.success(contentUrl);
        }
        return AiResult.failure(text(response, "failCode"), text(response, "failReason"));
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String token = apiKey.trim();
        if (!token.isEmpty()) {
            headers.setBearerAuth(token);
        }
        return headers;
    }

    private String safeMessage(RuntimeException exception) {
        return exception.getMessage() == null ? "模型调用失败" : exception.getMessage();
    }
}
