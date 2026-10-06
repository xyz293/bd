package com.xiaoa.ai.chat.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoa.ai.chat.dto.QOptionVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容模型适配器（chat/completions 协议，支持各类 OpenAI 兼容网关）：
 * 配置 xiaoa.ai.llm.type=openai 启用，base-url/api-key/model 可指向任意兼容服务。
 *
 * <p>系统提示词由发起请求的 Agent 携带（每个 Agent 自己的提示词，YAML xiaoa.ai.llm.prompts.* 可配，
 * 缺省按 mode 回退 {@link AgentPromptProperties} 的内置默认），本类不再拼装提示词，只按 mode 解析：</p>
 * <pre>
 * GATE          → {"intent":"NEW_CREATE|REVISE|CONSULT","ready":true|false,"missing":[],"options":[],"contextPatch":{...}}
 * OPTIONS       → {"question":"题干","options":[{"key","label","hint","slotKey"}]}
 * REACT         → {"thought":"...","action":"技能名或FINISH","actionInput":{}}
 * COMPOSE       → {"question":"..."}
 * REVISE        → {"action":"GENERATE","versions":["单版改写结果"]}
 * CHAT          → {"action":"GENERATE","versions":[...]} / {"action":"ASK","question":"..."}
 * </pre>
 *
 * <p>调用失败重试 1 次（与 HttpLlmProvider 一致），仍失败抛异常由图节点内兜底/回滚；
 * 模型输出偶发 markdown 围栏，解析前统一剥离取最外层 JSON 对象。</p>
 */
@Component
@ConditionalOnProperty(name = "xiaoa.ai.llm.type", havingValue = "openai")
public class OpenAiLlmProvider implements LlmProvider {

    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final AgentPromptProperties promptProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate;

    public OpenAiLlmProvider(AgentPromptProperties promptProperties,
                             @Value("${xiaoa.ai.llm.base-url:https://api.openai.com/v1}") String baseUrl,
                             @Value("${xiaoa.ai.llm.api-key:}") String apiKey,
                             @Value("${xiaoa.ai.llm.model:gpt-4o-mini}") String model,
                             @Value("${xiaoa.ai.llm.timeout-ms:60000}") long timeoutMs) {
        this.promptProperties = promptProperties;
        String trimmed = baseUrl == null ? "" : baseUrl.trim();
        this.baseUrl = trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(5).toMillis());
        factory.setReadTimeout((int) timeoutMs);
        this.restTemplate = new RestTemplate(factory);
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        if (apiKey.isEmpty()) {
            throw new IllegalStateException("LLM API Key 未配置（xiaoa.ai.llm.api-key）");
        }
        if (baseUrl.isEmpty()) {
            throw new IllegalStateException("LLM 服务地址未配置（xiaoa.ai.llm.base-url）");
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
            Map<String, Object> body = new LinkedHashMap<String, Object>();
            body.put("model", model);
            body.put("temperature", 0.7);
            List<Map<String, String>> messages = new ArrayList<Map<String, String>>();
            messages.add(message("system", systemPromptOf(request)));
            if (request.getMessages() != null && !request.getMessages().isEmpty()) {
                // REACT 多轮轨迹：任务/观察与思考交替，逐轮追加后重发
                messages.addAll(request.getMessages());
            } else {
                messages.add(message("user", userContentOf(request)));
            }
            body.put("messages", messages);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey == null ? "" : apiKey);
            String raw = restTemplate.postForObject(baseUrl + "/chat/completions",
                    new HttpEntity<String>(objectMapper.writeValueAsString(body), headers), String.class);

            JsonNode root = objectMapper.readTree(raw == null ? "" : raw);
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            JsonNode payload = extractJson(content);
            return parseByMode(request.getMode(), payload);
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("LLM 响应解析失败", exception);
        }
    }

    // ==================== 系统提示词（Agent 携带优先，缺省回退内置默认） ====================

    /**
     * 系统提示词解析：优先使用发起请求的 Agent 随请求携带的提示词
     * （每个 Agent 自己的提示词，YAML xiaoa.ai.llm.prompts.<agent> 可配）；
     * Agent 未携带时按 mode 回退 {@link AgentPromptProperties} 的内置默认。
     */
    private String systemPromptOf(LlmRequest request) {
        if (request.getSystemPrompt() != null && !request.getSystemPrompt().trim().isEmpty()) {
            return request.getSystemPrompt();
        }
        return promptProperties.byMode(request.getMode());
    }

    /** 结构化用户消息：模式 + 场景 + 槽位上下文 + 历史 + 本轮输入/原文案。 */
    private String userContentOf(LlmRequest request) {
        try {
            Map<String, Object> data = new LinkedHashMap<String, Object>();
            data.put("mode", request.getMode());
            if (request.getScene() != null) {
                data.put("scene", request.getScene());
            }
            data.put("context", parse(request.getContextJson()));
            data.put("history", parse(request.getHistoryJson()));
            if (request.getUserText() != null) {
                data.put("userText", request.getUserText());
            }
            if (request.getBaseText() != null) {
                data.put("baseText", request.getBaseText());
            }
            return objectMapper.writeValueAsString(data);
        } catch (Exception exception) {
            return String.valueOf(request.getUserText());
        }
    }

    // ==================== 响应解析 ====================

    private LlmResponse parseByMode(String mode, JsonNode node) {
        if (LlmRequest.MODE_GATE.equals(mode)) {
            LlmResponse response = LlmResponse.gate(node.path("ready").asBoolean(false), stringsOf(node.path("options")));
            response.setIntent(node.path("intent").asText(""));
            response.setMissing(stringsOf(node.path("missing")));
            response.setNeedSkills(stringsOf(node.path("needSkills")));
            response.setReason(node.path("reason").asText(null));
            response.setContextPatchJson(patchOf(node));
            return response;
        }
        if (LlmRequest.MODE_COMPOSE.equals(mode)) {
            return LlmResponse.compose(node.path("question").asText(""));
        }
        if (LlmRequest.MODE_OPTIONS.equals(mode)) {
            List<QOptionVO> cardOptions = new ArrayList<QOptionVO>();
            JsonNode optionNodes = node.path("options");
            if (optionNodes.isArray()) {
                optionNodes.forEach(item -> {
                    try {
                        cardOptions.add(objectMapper.treeToValue(item, QOptionVO.class));
                    } catch (Exception ignored) {
                        // 单个选项解析失败丢弃，由出题兜底补齐
                    }
                });
            }
            return LlmResponse.cardOptionsOf(node.path("question").asText(null),
                    cardOptions.isEmpty() ? null : cardOptions);
        }
        if (LlmRequest.MODE_REACT.equals(mode)) {
            JsonNode args = node.path("actionInput");
            return LlmResponse.reactStep(node.path("thought").asText(""),
                    node.path("action").asText(""), args.isObject() ? args.toString() : "{}");
        }
        // CHAT / REVISE：按 action 分派
        String action = node.path("action").asText("");
        if (LlmResponse.ACTION_GENERATE.equals(action)) {
            List<String> versions = new ArrayList<String>();
            JsonNode versionNodes = node.path("versions");
            if (versionNodes.isArray()) {
                versionNodes.forEach(item -> versions.add(item.asText("")));
            }
            return LlmResponse.generate(versions, patchOf(node));
        }
        if (LlmResponse.ACTION_ASK.equals(action)) {
            return LlmResponse.ask(node.path("question").asText("能再具体一点吗"), patchOf(node));
        }
        throw new IllegalStateException("LLM 返回的 action 不合法：" + action);
    }

    private String patchOf(JsonNode node) {
        JsonNode patch = node.path("contextPatch");
        return patch.isObject() ? patch.toString() : null;
    }

    /** JSON 字符串数组 → List（空数组返回 null，区分「未给」与「空」）。 */
    private List<String> stringsOf(JsonNode array) {
        List<String> values = new ArrayList<String>();
        if (array != null && array.isArray()) {
            array.forEach(item -> values.add(item.asText("")));
        }
        return values.isEmpty() ? null : values;
    }

    /** 剥离 markdown 围栏/闲话：取第一个 { 到最后一个 } 的片段解析。 */
    private JsonNode extractJson(String content) {
        String text = content == null ? "" : content.trim();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalStateException("LLM 返回内容不是 JSON");
        }
        try {
            return objectMapper.readTree(text.substring(start, end + 1));
        } catch (Exception exception) {
            throw new IllegalStateException("LLM 返回 JSON 解析失败", exception);
        }
    }

    private Map<String, String> message(String role, String content) {
        Map<String, String> message = new LinkedHashMap<String, String>();
        message.put("role", role);
        message.put("content", content);
        return message;
    }

    private JsonNode parse(String json) {
        try {
            return objectMapper.readTree(json == null || json.trim().isEmpty() ? "{}" : json);
        } catch (Exception exception) {
            return objectMapper.createObjectNode();
        }
    }
}
