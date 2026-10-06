package com.xiaoa.ai.chat.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoa.ai.chat.graph.ChatFlowState;
import com.xiaoa.ai.chat.model.ChatMessage;
import com.xiaoa.ai.chat.provider.AgentPromptProperties;
import com.xiaoa.ai.chat.provider.LlmRequest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 节点 Agent 基类：内置「隔离记忆读写」（remember/recall，按 name() 命名空间）、
 * 各节点常用的上下文解析辅助，以及「每个 Agent 自己的提示词」的注入与携带
 * （提示词 YAML 可配：xiaoa.ai.llm.prompts.*，未配置回退内置默认），子类只写业务逻辑。
 */
public abstract class BaseNodeAgent implements NodeAgent {

    protected static final String FALLBACK_QUESTION = "能再具体一点吗？";

    protected final AgentMemoryService agentMemory;
    /** 本 Agent 的提示词配置（YAML 注入，每个 Agent 一个 key） */
    protected final AgentPromptProperties prompts;
    protected final ObjectMapper objectMapper = new ObjectMapper();

    protected BaseNodeAgent(AgentMemoryService agentMemory, AgentPromptProperties prompts) {
        this.agentMemory = agentMemory;
        this.prompts = prompts;
    }

    // ==================== 隔离记忆（本 Agent 命名空间） ====================

    /** 写一条本 Agent 的业务记忆（只存当前节点需要处理的中间产物）。 */
    protected void remember(ChatFlowState state, String field, String value) {
        agentMemory.write(state.getThreadId(), name(), field, value);
    }

    /** 读一条本 Agent 的业务记忆，miss 返回 null。 */
    protected String recall(ChatFlowState state, String field) {
        return agentMemory.read(state.getThreadId(), name(), field);
    }

    // ==================== 消息构造 ====================

    protected ChatMessage message(ChatFlowState state, String role, String content) {
        ChatMessage message = new ChatMessage();
        message.setTenantId(state.tenantId());
        message.setSessionId(state.getSession().getId());
        message.setUserId(state.getSession().getUserId());
        message.setRole(role);
        message.setContent(content);
        return message;
    }

    // ==================== 出稿失败的模板兜底（与 demo provider 风格一致） ====================

    protected List<String> fallbackVersions(ChatFlowState state) {
        JsonNode context = parse(state.getContextJson());
        String product = defaultIfBlank(textOf(context, "product"), "我们家的产品");
        String sellingPoint = defaultIfBlank(textOf(context, "sellingPoint"), "品质出众");
        String audience = defaultIfBlank(textOf(context, "audience"), "每一位顾客");
        String scene = defaultIfBlank(state.getSession().getScene(), "朋友圈");
        String festival = textOf(context, "festival");
        String prefix = isBlank(festival) ? "" : festival + "将至，";
        int count = "4".equals(textOf(context, "versions")) ? 4 : 3;
        List<String> versions = new ArrayList<String>();
        versions.add("【" + scene + " · 情感版】" + prefix + product + "，为" + audience + "而生。" + sellingPoint
                + "，是它最动人的答案。这个" + scene + "，把心意交给我们。");
        versions.add("【" + scene + " · 种草版】" + prefix + "被问爆的" + product + "来了！主打" + sellingPoint + "，"
                + audience + "闭眼入，评论区扣1安排。");
        versions.add("【" + scene + " · 简洁版】" + product + "｜" + sellingPoint + "。适合" + audience + "，到店体验更优惠。");
        if (count >= 4) {
            versions.add("【" + scene + " · 场景版】戴上" + product + "的那一刻，" + sellingPoint
                    + "有了画面感。" + prefix + "这个" + scene + "，让心意被看见。");
        }
        return versions;
    }

    // ==================== LLM 请求（Agent 自带提示词） ====================

    /** 发起 LLM 请求：自动携带本 Agent 的提示词（YAML xiaoa.ai.llm.prompts.<agent> 可配）。 */
    protected LlmRequest withPrompt(LlmRequest request) {
        return withPrompt(request, systemPrompt());
    }

    /** 发起 LLM 请求（一个 Agent 承担多种 LLM 模式时显式指定提示词，如生成 Agent 的 chat/revise）。 */
    protected LlmRequest withPrompt(LlmRequest request, String systemPrompt) {
        request.setSystemPrompt(systemPrompt);
        return request;
    }

    // ==================== JSON / 字符串辅助 ====================

    /** 把槽位补丁合并回会话快照（浅合并，补丁值覆盖旧值；null 值跳过不污染）。 */
    protected String mergeContext(String contextJson, String patchJson) {
        try {
            JsonNode context = objectMapper.readTree(contextJson == null || contextJson.trim().isEmpty()
                    ? "{}" : contextJson);
            if (patchJson != null && !patchJson.trim().isEmpty()) {
                JsonNode patch = objectMapper.readTree(patchJson);
                if (patch.isObject()) {
                    Map<String, Object> merged = new LinkedHashMap<String, Object>();
                    context.fields().forEachRemaining(field -> merged.put(field.getKey(), field.getValue()));
                    patch.fields().forEachRemaining(field -> {
                        if (!field.getValue().isNull()) {
                            merged.put(field.getKey(), field.getValue().asText());
                        }
                    });
                    return objectMapper.writeValueAsString(merged);
                }
            }
            return contextJson == null || contextJson.trim().isEmpty() ? "{}" : contextJson;
        } catch (Exception exception) {
            return contextJson == null ? "{}" : contextJson;
        }
    }

    protected String patch(String key, String value) {
        try {
            return objectMapper.writeValueAsString(Collections.singletonMap(key, value));
        } catch (Exception exception) {
            return null;
        }
    }

    protected String jsonList(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (Exception exception) {
            return "[]";
        }
    }

    protected JsonNode parse(String json) {
        try {
            return objectMapper.readTree(json == null || json.trim().isEmpty() ? "{}" : json);
        } catch (Exception exception) {
            return objectMapper.createObjectNode();
        }
    }

    protected String textOf(JsonNode context, String field) {
        if (context == null || !context.hasNonNull(field)) {
            return null;
        }
        String value = context.get(field).asText("");
        return value.trim().isEmpty() ? null : value.trim();
    }

    protected boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    protected String defaultIfBlank(String value, String fallback) {
        return isBlank(value) ? fallback : value.trim();
    }

    protected boolean allBlank(List<String> values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    protected List<String> safeList(List<String> values) {
        return values == null ? new ArrayList<String>() : values;
    }
}
