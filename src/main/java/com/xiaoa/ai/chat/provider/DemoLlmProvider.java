package com.xiaoa.ai.chat.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xiaoa.ai.chat.dto.QOptionVO;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 演示 LLM：不调外部模型，按节点职责（意图+充分性判断/ReAct 收集/出稿/微调/组装）返回规则化结果，供开发联调。
 * 必填槽位：contentType（文案/图片/视频）→ theme（主题）→ product，与真实模型 Gate 提示词同序。
 */
@Component
@ConditionalOnProperty(name = "xiaoa.ai.llm.type", havingValue = "demo", matchIfMissing = true)
public class DemoLlmProvider implements LlmProvider {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public LlmResponse complete(LlmRequest request) {
        if (LlmRequest.MODE_GATE.equals(request.getMode())) {
            return gate(request);
        }
        if (LlmRequest.MODE_OPTIONS.equals(request.getMode())) {
            return options(request);
        }
        if (LlmRequest.MODE_REACT.equals(request.getMode())) {
            return react(request);
        }
        if (LlmRequest.MODE_COMPOSE.equals(request.getMode())) {
            return compose(request);
        }
        if (LlmRequest.MODE_REVISE.equals(request.getMode())) {
            String revised = request.getBaseText() == null ? "" : request.getBaseText();
            List<String> revisedVersions = new ArrayList<String>();
            revisedVersions.add(revised + "（已按「" + request.getUserText() + "」调整）");
            return LlmResponse.generate(revisedVersions, null);
        }
        return chat(request);
    }

    // ==================== ① Gate：意图识别 + 槽位抽取 + 充分性判断（demo 规则实现） ====================

    /**
     * 先识别意图（微调/咨询不进创作循环）；新创作再按必填槽位同序判断充分性：
     * 内容形态 → 主题 → 商品，缺哪个返回 not ready 并给出该维度候选方向（与选项卡出题表一致）；
     * 全齐 → 需要商品资料技能（节日未定加日历）。
     */
    private LlmResponse gate(LlmRequest request) {
        String text = request.getUserText() == null ? "" : request.getUserText().trim();
        JsonNode context = parseContext(request.getContextJson());
        if (text.matches(".*(改一下|微调|换个说法|再活泼|正式一点|再简短|再长一点).*")) {
            LlmResponse response = LlmResponse.gate(true, null);
            response.setIntent(LlmResponse.INTENT_REVISE);
            return response;
        }
        if (text.length() <= 12 && text.matches(".*(你好|您好|在吗|谢谢|辛苦|哈哈|嗯嗯|好的|收到|怎么卖|多少钱|营业).*")) {
            LlmResponse response = LlmResponse.gate(true, null);
            response.setIntent(LlmResponse.INTENT_CONSULT);
            return response;
        }
        // 先抽取本轮已明确表达的槽位，合并为「生效上下文」再判断（与真实模型一致：本轮说了就算已确认，不重复问）
        String patchJson = extractSlots(context, text);
        JsonNode effective = context;
        if (patchJson != null) {
            effective = merge(context, patchJson);
        }
        LlmResponse response;
        if ("true".equals(textOf(context, "directGenerate"))) {
            // AI 代选（超时兜底/「你帮我定」）：为缺失槽位给出推荐值并直接放行（demo 规则模拟真实模型思考）
            response = LlmResponse.gate(true, null);
            java.util.Map<String, String> decided = new java.util.LinkedHashMap<String, String>();
            if (isBlank(textOf(effective, "contentType"))) {
                decided.put("contentType", "文案");
            }
            if (isBlank(textOf(effective, "theme"))) {
                decided.put("theme", "日常种草");
            }
            if (isBlank(textOf(effective, "product"))) {
                decided.put("product", "本店最近热销款");
            }
            if (!decided.isEmpty()) {
                response.setReason("AI 代选：" + decided);
                patchJson = mergePatches(patchJson, decided);
            }
        } else if (isBlank(textOf(effective, "contentType"))) {
            List<String> options = new ArrayList<String>();
            options.add("文案：纯文字，发圈最快");
            options.add("图片配文：实拍图+种草文");
            options.add("视频脚本：照着拍就能发");
            response = LlmResponse.gate(false, options);
            response.setMissing(java.util.Collections.singletonList("contentType"));
        } else if (isBlank(textOf(effective, "theme"))) {
            List<String> options = new ArrayList<String>();
            options.add("节日氛围：蹭热点更容易被转发");
            options.add("爱情婚嫁：情感向内容");
            options.add("日常种草：百搭不过时");
            response = LlmResponse.gate(false, options);
            response.setMissing(java.util.Collections.singletonList("theme"));
        } else if (isBlank(textOf(effective, "product"))) {
            List<String> options = new ArrayList<String>();
            options.add("主推本店最近热销款");
            options.add("宣传新品转运珠");
            options.add("做婚戒对戒情感向");
            response = LlmResponse.gate(false, options);
            response.setMissing(java.util.Collections.singletonList("product"));
        } else {
            List<String> skills = new ArrayList<String>();
            skills.add("queryProduct");
            if (isBlank(textOf(effective, "festival"))) {
                skills.add("queryCalendar");
            }
            response = LlmResponse.gate(true, null);
            response.setNeedSkills(skills);
        }
        response.setIntent(LlmResponse.INTENT_NEW_CREATE);
        response.setContextPatchJson(patchJson);
        return response;
    }

    // ==================== ② 选项卡出题（demo 模拟真实模型思考） ====================

    /**
     * 选项卡生成：按「生效上下文」找第一个缺口出题（与 gate 判断同序），
     * 已确认/已问过的维度不再出；每条选项都归属对应槽位，点选后回填。
     */
    private LlmResponse options(LlmRequest request) {
        JsonNode context = parseContext(request.getContextJson());
        if (isBlank(textOf(context, "contentType"))) {
            return demoCard("想做哪种形态的内容？",
                    new String[][]{{"文案", "纯文字，发圈最快"}, {"图片配文", "实拍图+种草文"}, {"视频脚本", "照着拍就能发"}},
                    "contentType");
        }
        if (isBlank(textOf(context, "theme"))) {
            return demoCard("内容往哪个方向走？",
                    new String[][]{{"节日氛围", "蹭热点更容易被转发"}, {"爱情婚嫁", "情感向内容"}, {"日常种草", "百搭不过时"}},
                    "theme");
        }
        if (isBlank(textOf(context, "product"))) {
            return demoCard("围绕哪件商品来写？",
                    new String[][]{{"最近热销款", "先卖已验证的爆款"}, {"新品转运珠", "新品上市重点曝光"}, {"婚戒对戒", "情感向故事更好写"}},
                    "product");
        }
        if (isBlank(textOf(context, "platform"))) {
            return demoCard("发在哪？",
                    new String[][]{{"朋友圈", "熟客日常触达"}, {"抖音", "公域流量更大"}, {"小红书", "种草笔记质感高"}},
                    "platform");
        }
        return demoCard("还有什么想强调的？",
                new String[][]{{"突出价格优惠", "促销力度更大"}, {"突出品质工艺", "建立信任感"}, {"突出售后保障", "打消下单顾虑"}},
                "sellingPoint");
    }

    /** demo 出题：label/hint/slotKey 归属对应槽位，key 由 OptionAgent 统一重编。 */
    private LlmResponse demoCard(String question, String[][] defs, String slotKey) {
        List<QOptionVO> cardOptions = new ArrayList<QOptionVO>();
        for (String[] def : defs) {
            cardOptions.add(new QOptionVO(null, def[0], def[1], slotKey));
        }
        return LlmResponse.cardOptionsOf(question, cardOptions);
    }

    /**
     * 槽位抽取（key 必须在预注册字典内，每次只抽一个，与充分性判断同序）：
     * contentType（文案/图片/视频关键词） → theme（节日/婚嫁/种草关键词） → product → sellingPoint → audience。
     */
    private String extractSlots(JsonNode context, String text) {
        if (text.isEmpty()) {
            return null;
        }
        if (isBlank(textOf(context, "contentType"))) {
            if (text.matches(".*(视频|短视频|口播|分镜|拍摄|脚本).*")) {
                return patch("contentType", "视频");
            }
            if (text.matches(".*(图片|配图|海报|实拍|照片|九宫格|图文).*")) {
                return patch("contentType", "图片");
            }
            if (text.matches(".*(文案|文字|写一段|写几条|配文).*")) {
                return patch("contentType", "文案");
            }
            return null; // 形态说不清 → 交给选项卡引导
        }
        if (isBlank(textOf(context, "theme"))) {
            if (text.matches(".*(情人节|七夕|圣诞|元旦|春节|中秋|周年庆|节日|节点).*")) {
                return patch("theme", "节日氛围");
            }
            if (text.matches(".*(对戒|婚戒|求婚|结婚|爱情|表白|婚嫁|情侣).*")) {
                return patch("theme", "爱情婚嫁");
            }
            if (text.matches(".*(种草|推荐|安利|日常|分享).*")) {
                return patch("theme", "日常种草");
            }
            return null;
        }
        if (isBlank(textOf(context, "product"))) {
            return patch("product", firstLine(text));
        }
        if (isBlank(textOf(context, "sellingPoint"))) {
            return patch("sellingPoint", text);
        }
        if (isBlank(textOf(context, "audience"))) {
            return patch("audience", text);
        }
        return null;
    }

    // ==================== ReAct 技能收集（demo 规则实现） ====================

    /**
     * 规则版 ReAct：第一轮查商品资料；节日未定则查日历；随后 FINISH。
     * 轨迹 messages 由 SkillAgent 逐轮追加（首条任务 + assistant/user 交替）。
     */
    private LlmResponse react(LlmRequest request) {
        java.util.List<java.util.Map<String, String>> messages = request.getMessages();
        int doneSteps = messages == null ? 0 : (messages.size() - 1) / 2; // 每步 = assistant+user 两条
        JsonNode context = parseContext(request.getContextJson());
        if (doneSteps < 1) {
            return LlmResponse.reactStep("先查询会话内已确认的商品资料", "queryProduct", "{}");
        }
        if (doneSteps < 3 && !queriedTool(messages, "queryCalendar") && isBlank(textOf(context, "festival"))) {
            return LlmResponse.reactStep("商品已查到；节日未定，查营销日历补充节日元素", "queryCalendar", "{}");
        }
        return LlmResponse.reactStep("资料已足够，结束收集", "FINISH", "{}");
    }

    /** 轨迹里是否已调用过某技能（避免重复）。 */
    private boolean queriedTool(java.util.List<java.util.Map<String, String>> messages, String toolName) {
        if (messages == null) {
            return false;
        }
        for (java.util.Map<String, String> message : messages) {
            if ("assistant".equals(message.get("role"))
                    && message.get("content") != null
                    && message.get("content").contains("\"" + toolName + "\"")) {
                return true;
            }
        }
        return false;
    }

    /** 口语化组装：槽位齐备视为出稿包装场景，否则视为咨询/闲聊回复。 */
    private LlmResponse compose(LlmRequest request) {
        JsonNode context = parseContext(request.getContextJson());
        String product = textOf(context, "product");
        if (!isBlank(product)) {
            return LlmResponse.compose("已按「" + product + "」出好内容，点任意一版可直接微调～");
        }
        String text = request.getUserText() == null ? "" : request.getUserText().trim();
        if (text.isEmpty()) {
            return LlmResponse.compose("收到～想出文案随时说！");
        }
        return LlmResponse.compose("收到～关于「" + firstLine(text) + "」，你可以直接说想做什么内容，我来一步步帮你搞定！");
    }

    // ==================== 生成 / 微调 ====================

    private LlmResponse chat(LlmRequest request) {
        JsonNode context = parseContext(request.getContextJson());
        String product = textOf(context, "product");
        String sellingPoint = textOf(context, "sellingPoint");
        String audience = textOf(context, "audience");
        String audienceValue = isBlank(audience) ? "每一位顾客" : audience;
        String point = isBlank(sellingPoint) ? "品质出众" : sellingPoint;
        String goods = isBlank(product) ? "我们家的产品" : product;
        return LlmResponse.generate(versions(request.getScene(), goods, point, audienceValue, context), null);
    }

    /** 按槽位 versions 出 3 版（增益选「多备一版」时 4 版）；价格等缺失字段保留占位符。 */
    private List<String> versions(String scene, String product, String sellingPoint, String audience, JsonNode context) {
        String tag = isBlank(textOf(context, "platform")) ? (scene == null || scene.isEmpty() ? "朋友圈" : scene) : textOf(context, "platform");
        String festival = textOf(context, "festival");
        String festivalPrefix = isBlank(festival) ? "" : festival + "将至，";
        String tone = "正式".equals(textOf(context, "tone")) ? "。" : "～";
        int count = 3;
        String versionsSlot = textOf(context, "versions");
        if ("4".equals(versionsSlot)) {
            count = 4;
        }
        List<String> list = new ArrayList<String>();
        list.add("【" + tag + " · 情感版】" + festivalPrefix + product + "，为" + audience + "而生。" + sellingPoint
                + "，是它最动人的答案。这个" + tag + "，把心意交给我们" + tone);
        list.add("【" + tag + " · 种草版】" + festivalPrefix + "被问爆的" + product + "来了！主打" + sellingPoint + "，"
                + audience + "闭眼入，评论区扣1安排～");
        list.add("【" + tag + " · 简洁版】" + product + "｜" + sellingPoint + "。适合" + audience + "，到店体验更优惠。");
        if (count >= 4) {
            list.add("【" + tag + " · 场景版】戴上" + product + "的那一刻，" + sellingPoint
                    + "有了画面感。" + festivalPrefix + "这个" + tag + "，让心意被看见～");
        }
        return list;
    }

    // ==================== 辅助 ====================

    private JsonNode parseContext(String contextJson) {
        try {
            if (contextJson == null || contextJson.trim().isEmpty()) {
                return objectMapper.createObjectNode();
            }
            return objectMapper.readTree(contextJson);
        } catch (Exception exception) {
            return objectMapper.createObjectNode();
        }
    }

    private String textOf(JsonNode context, String field) {
        if (context == null || !context.hasNonNull(field)) {
            return null;
        }
        String value = context.get(field).asText("");
        return value.trim().isEmpty() ? null : value.trim();
    }

    private String patch(String key, String value) {
        try {
            return objectMapper.writeValueAsString(java.util.Collections.singletonMap(key, value));
        } catch (Exception exception) {
            return null;
        }
    }

    /** 合并两个槽位补丁 JSON（后写覆盖前写），用于 AI 代选值并入抽取结果。 */
    private String mergePatches(String baseJson, java.util.Map<String, String> overrides) {
        try {
            ObjectNode merged = objectMapper.createObjectNode();
            if (baseJson != null && !baseJson.trim().isEmpty()) {
                JsonNode baseNode = objectMapper.readTree(baseJson);
                if (baseNode != null && baseNode.isObject()) {
                    merged.setAll((ObjectNode) baseNode);
                }
            }
            overrides.forEach(merged::put);
            return merged.size() == 0 ? null : objectMapper.writeValueAsString(merged);
        } catch (Exception exception) {
            return baseJson;
        }
    }

    /** 把本轮抽取到的槽位补丁合并进上下文副本，得到判断用的「生效上下文」。 */
    private JsonNode merge(JsonNode base, String patchJson) {
        try {
            JsonNode patchNode = objectMapper.readTree(patchJson);
            if (patchNode != null && patchNode.isObject() && base.isObject()) {
                ObjectNode copy = objectMapper.createObjectNode();
                copy.setAll((ObjectNode) base);
                copy.setAll((ObjectNode) patchNode);
                return copy;
            }
        } catch (Exception ignored) {
            // 补丁非法时退回原上下文
        }
        return base;
    }

    private String firstLine(String text) {
        if (text == null) {
            return "";
        }
        String trimmed = text.trim();
        return trimmed.length() > 24 ? trimmed.substring(0, 24) : trimmed;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
