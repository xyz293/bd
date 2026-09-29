package com.xiaoa.ai.chat.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoa.ai.chat.dto.QOptionVO;
import com.xiaoa.ai.chat.dto.QuestionVO;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 演示 LLM：不调外部模型，按节点职责（意图+抽槽位/动态问卷/出稿/微调/组装）返回规则化结果，供开发联调。
 * 必填槽位：product（缺口由图代码统一计算，本类只负责抽取与出题）。
 */
@Component
@ConditionalOnProperty(name = "xiaoa.ai.llm.type", havingValue = "demo", matchIfMissing = true)
public class DemoLlmProvider implements LlmProvider {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public LlmResponse complete(LlmRequest request) {
        if (LlmRequest.MODE_INTENT.equals(request.getMode())) {
            return intent(request);
        }
        if (LlmRequest.MODE_QUESTIONNAIRE.equals(request.getMode())) {
            return questionnaire(request);
        }
        if (LlmRequest.MODE_GATE.equals(request.getMode())) {
            return gate(request);
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

    // ==================== ② understand_intent（意图+抽槽位，demo 规则实现） ====================

    /**
     * 意图三分类（新创作/微调/咨询）+ 任务类型 + 槽位抽取：
     * 缺口与增益候选由图代码计算，LLM 只产出 intent/taskType/slots。
     */
    private LlmResponse intent(LlmRequest request) {
        String text = request.getUserText() == null ? "" : request.getUserText().trim();
        LlmResponse response = new LlmResponse();
        JsonNode context = parseContext(request.getContextJson());
        if (text.matches(".*(改一下|微调|换个说法|再活泼|正式一点|再简短|再长一点).*")) {
            response.setAction(LlmResponse.ACTION_ASK);
            response.setIntent(LlmResponse.INTENT_REVISE);
            response.setTaskType(LlmResponse.TASK_COPY);
            return response;
        }
        if (text.length() <= 12 && text.matches(".*(你好|您好|在吗|谢谢|辛苦|哈哈|嗯嗯|好的|收到|怎么卖|多少钱|营业).*")) {
            response.setAction(LlmResponse.ACTION_ASK);
            response.setIntent(LlmResponse.INTENT_CONSULT);
            response.setTaskType(LlmResponse.TASK_COPY);
            return response;
        }
        response.setAction(LlmResponse.ACTION_ASK);
        response.setIntent(LlmResponse.INTENT_NEW_CREATE);
        response.setTaskType(taskTypeOf(text));
        response.setContextPatchJson(extractSlots(context, text));
        return response;
    }

    /** 任务类型：视频 > 配图 > 文案。 */
    private String taskTypeOf(String text) {
        if (text.matches(".*(视频|短片|vlog).*")) {
            return LlmResponse.TASK_VIDEO;
        }
        if (text.matches(".*(图|海报|配图|插画).*")) {
            return LlmResponse.TASK_IMAGE;
        }
        return LlmResponse.TASK_COPY;
    }

    /** 槽位抽取（key 必须在预注册字典内）：依次填 product → sellingPoint → audience。 */
    private String extractSlots(JsonNode context, String text) {
        if (text.isEmpty()) {
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

    // ==================== ③ compose_questionnaire（动态问卷，demo 规则实现） ====================

    /** 针对 missing 必填项出题：每轮 ≤3 题、每题 2~4 选项、支持「AI 代选」。 */
    private LlmResponse questionnaire(LlmRequest request) {
        List<String> missing = parseMissing(request.getUserText());
        return LlmResponse.questionsOf(buildQuestions(missing));
    }

    /** 兜底问卷同款规则（LLM 失败时图内降级也走这一套）。 */
    public static List<QuestionVO> buildQuestions(List<String> missing) {
        List<QuestionVO> questions = new ArrayList<QuestionVO>();
        for (String slotKey : missing) {
            if ("product".equals(slotKey)) {
                questions.add(productQuestion());
            } else if ("platform".equals(slotKey)) {
                questions.add(platformQuestion());
            } else {
                questions.add(genericQuestion(slotKey));
            }
            if (questions.size() >= 3) {
                break;
            }
        }
        return questions;
    }

    private static QuestionVO productQuestion() {
        QuestionVO question = new QuestionVO();
        question.setId("q_product");
        question.setSlotKey("product");
        question.setQuestion("这次推哪个商品？");
        List<QOptionVO> options = new ArrayList<QOptionVO>();
        options.add(new QOptionVO("A", "对戒（520热销）", "婚恋人群，情感向文案"));
        options.add(new QOptionVO("B", "转运珠（新上柜）", "年轻客群，转运梗"));
        options.add(new QOptionVO("C", "从图库选", "稍后传实拍图"));
        options.add(new QOptionVO("D", "你帮我定", "AI按最近热销选"));
        question.setOptions(options);
        return question;
    }

    private static QuestionVO platformQuestion() {
        QuestionVO question = new QuestionVO();
        question.setId("q_platform");
        question.setSlotKey("platform");
        question.setQuestion("发到哪个平台？");
        List<QOptionVO> options = new ArrayList<QOptionVO>();
        options.add(new QOptionVO("A", "朋友圈", "私域熟客，口语化"));
        options.add(new QOptionVO("B", "小红书", "种草笔记体"));
        options.add(new QOptionVO("C", "抖音", "短视频口播"));
        question.setOptions(options);
        return question;
    }

    private static QuestionVO genericQuestion(String slotKey) {
        QuestionVO question = new QuestionVO();
        question.setId("q_" + slotKey);
        question.setSlotKey(slotKey);
        question.setQuestion("关于「" + slotKey + "」补充一点信息？");
        List<QOptionVO> options = new ArrayList<QOptionVO>();
        options.add(new QOptionVO("A", "按推荐来", "AI 结合上下文选"));
        options.add(new QOptionVO("B", "我来说明", "选完在输入框补充"));
        question.setOptions(options);
        return question;
    }

    // ==================== ⑤/⑦ 辅助模式（选项卡润色 / 口语化组装） ====================

    /** 完整度判断（Gate）：缺商品 → 不够并给候选方向；够 → 默认需要商品资料技能（节日未定加日历）。 */
    private LlmResponse gate(LlmRequest request) {
        JsonNode context = parseContext(request.getContextJson());
        if (isBlank(textOf(context, "product"))) {
            List<String> options = new ArrayList<String>();
            options.add("主推本店最近热销款");
            options.add("宣传新品转运珠");
            options.add("做婚戒对戒情感向");
            LlmResponse response = LlmResponse.gate(false, options);
            response.setMissing(java.util.Collections.singletonList("商品方向"));
            return response;
        }
        List<String> skills = new ArrayList<String>();
        skills.add("queryProduct");
        if (isBlank(textOf(context, "festival"))) {
            skills.add("queryCalendar");
        }
        LlmResponse response = LlmResponse.gate(true, null);
        response.setNeedSkills(skills);
        return response;
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

    // ==================== ⑥ 生成 / 微调 ====================

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

    private List<String> parseMissing(String missingJson) {
        List<String> missing = new ArrayList<String>();
        try {
            JsonNode node = objectMapper.readTree(missingJson == null || missingJson.trim().isEmpty() ? "[]" : missingJson);
            if (node.isArray()) {
                node.forEach(item -> missing.add(item.asText("")));
            }
        } catch (Exception ignored) {
            // 解析失败返回空列表，由图内兜底问卷处理
        }
        return missing;
    }

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
