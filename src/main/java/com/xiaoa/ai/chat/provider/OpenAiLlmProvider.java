package com.xiaoa.ai.chat.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoa.ai.chat.dto.QuestionVO;
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
 * <p>各节点模式通过 system prompt 约束「只输出 JSON」，按 mode 解析：</p>
 * <pre>
 * INTENT        → {"intent":"NEW_CREATE|REVISE|CONSULT","taskType":"COPY|IMAGE|VIDEO","contextPatch":{...}}
 * QUESTIONNAIRE → {"questions":[{"id","slotKey","question","options":[{"key","label","hint"}],...}]}
 * GATE          → {"ready":true|false,"options":["..."]}
 * COMPOSE       → {"question":"..."}
 * CHAT          → {"action":"GENERATE","versions":[...]} / {"action":"ASK","question":"..."}
 * REVISE        → {"action":"GENERATE","versions":["单版改写结果"]}
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
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate;

    public OpenAiLlmProvider(@Value("${xiaoa.ai.llm.base-url:https://api.openai.com/v1}") String baseUrl,
                             @Value("${xiaoa.ai.llm.api-key:}") String apiKey,
                             @Value("${xiaoa.ai.llm.model:gpt-4o-mini}") String model,
                             @Value("${xiaoa.ai.llm.timeout-ms:60000}") long timeoutMs) {
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
            messages.add(message("user", userContentOf(request)));
            body.put("messages", messages);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);
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

    // ==================== 各节点模式的 system prompt（珠宝门店垂类约束） ====================

    private String systemPromptOf(LlmRequest request) {
        String mode = request.getMode();
        if (LlmRequest.MODE_INTENT.equals(mode)) {
            return "你是珠宝门店「小AI」的对话创作助手，负责理解员工的创作需求。"
                    + "只输出一个 JSON 对象，禁止输出解释或 markdown 代码块："
                    + "{\"intent\":\"NEW_CREATE|REVISE|CONSULT\",\"taskType\":\"COPY|IMAGE|VIDEO\",\"contextPatch\":{...}}\n"
                    + "- intent：REVISE=员工要求修改/微调已有文案；CONSULT=问候/闲聊/咨询（不是要创作）；其它一律 NEW_CREATE\n"
                    + "- taskType：明确要视频→VIDEO；明确要配图/海报/图片→IMAGE；否则 COPY\n"
                    + "- contextPatch：从员工输入抽取的槽位补丁，key 只允许 platform/scene/product/style/tone/"
                    + "festival/versions/sellingPoint/audience/price/material；没抽到就输出 {}";
        }
        if (LlmRequest.MODE_QUESTIONNAIRE.equals(mode)) {
            return "你是珠宝门店「小AI」，员工要创作内容但缺少必填信息，需要出选择题引导。"
                    + "只输出 JSON，禁止多余文本："
                    + "{\"questions\":[{\"id\":\"q_xxx\",\"slotKey\":\"缺失槽位名\",\"question\":\"题干\","
                    + "\"options\":[{\"key\":\"A\",\"label\":\"选项文字\",\"hint\":\"一句话说明\"}],"
                    + "\"maxSelect\":1,\"allowAiDecide\":true}]}\n"
                    + "- 只针对用户消息给出的缺失槽位出题，slotKey 必须原样使用缺失槽位名，不得发明\n"
                    + "- 每轮最多 3 题；每题 2~4 个选项；选项贴合珠宝门店实际（商品题可给热销款、图库选等）\n"
                    + "- allowAiDecide=true 的题最后一项给「你帮我定」类选项（AI 按推荐代选）";
        }
        if (LlmRequest.MODE_REVISE.equals(mode)) {
            return "你是珠宝门店的文案改写助手，按修改指令改写原文案，保持原意、平台与格式。"
                    + "只输出 JSON，禁止多余文本：{\"action\":\"GENERATE\",\"versions\":[\"改写后的一版文案\"]}\n"
                    + "严禁出现「最高级」「投资价值」「保值升值」等珠宝行业违规表述。";
        }
        if (LlmRequest.MODE_COMPOSE.equals(mode)) {
            return "你是珠宝门店「小AI」的对话助手，用轻松的口语化中文回复员工，一两句话即可。"
                    + "只输出 JSON，禁止多余文本：{\"question\":\"你的回复\"}";
        }
        if (LlmRequest.MODE_GATE.equals(mode)) {
            return "判断当前上下文槽位是否足够生成珠宝内容文案。只输出 JSON，禁止多余文本："
                    + "{\"ready\":true|false,\"options\":[\"建议员工补充的点\"]}\n"
                    + "ready=false 时 options 给 2~3 条员工可能想说的快捷回复。";
        }
        // MODE_CHAT（及默认）：内容创作
        return "你是珠宝门店的资深内容策划，为员工写朋友圈/小红书/抖音文案。"
                + "只输出 JSON，禁止多余文本：{\"action\":\"GENERATE\",\"versions\":[\"文案1\",\"文案2\",\"文案3\"]}\n"
                + "- 默认 3 版；当上下文 versions==\"4\" 时出 4 版\n"
                + "- 每版以【平台 · 风格版名】开头（如【朋友圈 · 情感版】），平台/风格/语气/节日元素按上下文槽位执行\n"
                + "- 上下文 facts 中要求占位符的信息（如 [待核实：价格]）必须原样保留占位符，不得编造具体数值\n"
                + "- 严禁出现「最高级」「投资价值」「保值升值」等珠宝行业违规表述；上下文 complianceHint 有值时必须遵守";
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
        if (LlmRequest.MODE_INTENT.equals(mode)) {
            LlmResponse response = LlmResponse.intentOf(node.path("intent").asText(""));
            response.setTaskType(node.path("taskType").asText(""));
            response.setContextPatchJson(patchOf(node));
            return response;
        }
        if (LlmRequest.MODE_QUESTIONNAIRE.equals(mode)) {
            List<QuestionVO> questions = new ArrayList<QuestionVO>();
            JsonNode questionNodes = node.path("questions");
            if (questionNodes.isArray()) {
                questionNodes.forEach(item -> {
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
            List<String> options = new ArrayList<String>();
            JsonNode optionNodes = node.path("options");
            if (optionNodes.isArray()) {
                optionNodes.forEach(item -> options.add(item.asText("")));
            }
            return LlmResponse.gate(node.path("ready").asBoolean(false), options.isEmpty() ? null : options);
        }
        if (LlmRequest.MODE_COMPOSE.equals(mode)) {
            return LlmResponse.compose(node.path("question").asText(""));
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
