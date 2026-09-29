package com.xiaoa.ai.chat.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoa.ai.chat.dto.QuestionVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HTTP 文案模型适配器：POST xiaoa.ai.llm.url，请求/响应均为 JSON。
 * 按 mode 返回节点结构化结果：
 * INTENT → {"intent":"NEW_CREATE|REVISE|CONSULT","taskType":"COPY|IMAGE|VIDEO","contextPatch":{...}}
 * QUESTIONNAIRE → {"questions":[{"id","slotKey","question","options":[{"key","label","hint"}],"maxSelect","allowAiDecide"}]}
 * GATE → {"ready":true|false,"options":["..."]}
 * COMPOSE → {"question":"..."}
 * CHAT/REVISE → {"action":"ASK|GENERATE","question":"...","contextPatch":{...},"versions":["..."]}
 * 返回非法 JSON 重试 1 次，仍失败抛出异常，由图节点内兜底。
 */
@Component
@ConditionalOnProperty(name = "xiaoa.ai.llm.type", havingValue = "http")
public class HttpLlmProvider implements LlmProvider {

    private final String url;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate;

    public HttpLlmProvider(@Value("${xiaoa.ai.llm.url:}") String url) {
        this.url = url == null ? "" : url.trim();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(5).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(30).toMillis());
        this.restTemplate = new RestTemplate(factory);
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        if (url.isEmpty()) {
            throw new IllegalStateException("LLM 服务未配置（xiaoa.ai.llm.url）");
        }
        RuntimeException last = new IllegalStateException("LLM 服务调用失败");
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                return doComplete(request);
            } catch (RuntimeException exception) {
                last = exception;
            }
        }
        throw last;
    }

    private LlmResponse doComplete(LlmRequest request) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("mode", request.getMode());
            body.put("scene", request.getScene());
            body.put("context", request.getContextJson());
            body.put("history", request.getHistoryJson());
            body.put("userText", request.getUserText());
            body.put("baseText", request.getBaseText());
            String raw = restTemplate.postForObject(url, body, String.class);
            JsonNode node = objectMapper.readTree(raw == null ? "" : raw);
            String mode = request.getMode();
            if (LlmRequest.MODE_INTENT.equals(mode)) {
                LlmResponse response = LlmResponse.intentOf(node.path("intent").asText(""));
                response.setTaskType(node.path("taskType").asText(""));
                response.setContextPatchJson(node.path("contextPatch").isNull()
                        ? null : node.path("contextPatch").toString());
                return response;
            }
            if (LlmRequest.MODE_QUESTIONNAIRE.equals(mode)) {
                List<QuestionVO> questions = new ArrayList<>();
                JsonNode questionNode = node.path("questions");
                if (questionNode.isArray()) {
                    questionNode.forEach(item -> {
                        try {
                            questions.add(objectMapper.treeToValue(item, QuestionVO.class));
                        } catch (Exception ignored) {
                            // 单题解析失败丢弃，由图内校验兜底
                        }
                    });
                }
                return LlmResponse.questionsOf(questions.isEmpty() ? null : questions);
            }
            if (LlmRequest.MODE_GATE.equals(mode)) {
                List<String> options = new ArrayList<>();
                JsonNode optionNode = node.path("options");
                if (optionNode.isArray()) {
                    optionNode.forEach(item -> options.add(item.asText("")));
                }
                return LlmResponse.gate(node.path("ready").asBoolean(false), options.isEmpty() ? null : options);
            }
            if (LlmRequest.MODE_COMPOSE.equals(mode)) {
                return LlmResponse.compose(node.path("question").asText(""));
            }
            String action = node.path("action").asText("");
            if (LlmResponse.ACTION_GENERATE.equals(action)) {
                List<String> versions = new ArrayList<>();
                JsonNode versionNode = node.path("versions");
                if (versionNode.isArray()) {
                    versionNode.forEach(item -> versions.add(item.asText("")));
                }
                return LlmResponse.generate(versions, node.path("contextPatch").isNull() ? null
                        : node.path("contextPatch").toString());
            }
            if (LlmResponse.ACTION_ASK.equals(action)) {
                return LlmResponse.ask(node.path("question").asText("能再具体一点吗"),
                        node.path("contextPatch").isNull() ? null : node.path("contextPatch").toString());
            }
            throw new IllegalStateException("LLM 返回的 action 不合法：" + action);
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("LLM 响应解析失败", exception);
        }
    }
}
