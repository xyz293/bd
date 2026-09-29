package com.xiaoa.ai.chat.agent.skill;

import com.xiaoa.ai.chat.graph.ChatFlowState;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 商品资料技能（queryProduct）：聚合当前会话已确认的商品相关槽位
 * （名称/卖点/材质/价格/受众等），只取会话内真实存在的资料，绝不臆造；
 * 缺失字段如实标注「未提供」，由下游事实核验转占位符。
 */
@Component
public class ProductProfileSkill implements Skill {

    /** 可聚合的商品资料字段（与意图 Agent 的槽位字典一致） */
    private static final String[] PROFILE_FIELDS = {
            "product", "sellingPoint", "material", "price", "audience", "scene", "style", "tone"
    };

    @Override
    public String name() {
        return "queryProduct";
    }

    @Override
    public String description() {
        return "查询当前会话已确认的商品资料（名称/卖点/材质/价格/受众），来源于会话槽位，缺失如实标注";
    }

    @Override
    public String invoke(ChatFlowState state, com.fasterxml.jackson.databind.JsonNode args) {
        com.fasterxml.jackson.databind.JsonNode context = parse(state.getContextJson());
        Map<String, Object> profile = new LinkedHashMap<String, Object>();
        StringBuilder missing = new StringBuilder();
        for (String field : PROFILE_FIELDS) {
            String value = textOf(context, field);
            profile.put(field, value == null ? "未提供" : value);
            if (value == null) {
                if (missing.length() > 0) {
                    missing.append(",");
                }
                missing.append(field);
            }
        }
        profile.put("missingFields", missing.toString());
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(profile);
        } catch (Exception exception) {
            return "{\"error\":\"商品资料序列化失败\"}";
        }
    }

    private com.fasterxml.jackson.databind.JsonNode parse(String json) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .readTree(json == null || json.trim().isEmpty() ? "{}" : json);
        } catch (Exception exception) {
            return new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();
        }
    }

    private String textOf(com.fasterxml.jackson.databind.JsonNode context, String field) {
        if (context == null || !context.hasNonNull(field)) {
            return null;
        }
        String value = context.get(field).asText("");
        return value.trim().isEmpty() ? null : value.trim();
    }
}
