package com.xiaoa.ai.chat.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 演示 LLM：不调外部模型，按要素收集状态机返回追问或 3 版模板文案，供开发联调。
 * 要素齐备判定：product / sellingPoint / audience 均非空。
 */
@Component
@ConditionalOnProperty(name = "xiaoa.ai.llm.type", havingValue = "demo", matchIfMissing = true)
public class DemoLlmProvider implements LlmProvider {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public LlmResponse complete(LlmRequest request) {
        if (LlmRequest.MODE_REVISE.equals(request.getMode())) {
            String revised = request.getBaseText() == null ? "" : request.getBaseText();
            List<String> revisedVersions = new ArrayList<String>();
            revisedVersions.add(revised + "（已按「" + request.getUserText() + "」调整）");
            return LlmResponse.generate(revisedVersions, null);
        }
        return chat(request);
    }

    private LlmResponse chat(LlmRequest request) {
        JsonNode context = parseContext(request.getContextJson());
        String product = textOf(context, "product");
        String sellingPoint = textOf(context, "sellingPoint");
        String audience = textOf(context, "audience");
        String patch;
        if (product == null || product.isEmpty()) {
            // 首轮：把这句话当主题记下来，追问卖点
            patch = patch("product", firstLine(request.getUserText()));
            return LlmResponse.ask("好的～想突出什么卖点？（价格优惠 / 工艺品质 / 独特寓意）", patch);
        }
        if (sellingPoint == null || sellingPoint.isEmpty()) {
            patch = patch("sellingPoint", request.getUserText());
            return LlmResponse.ask("收到！目标客群是谁？（新婚 / 纪念日 / 日常送礼）", patch);
        }
        if (audience == null || audience.isEmpty()) {
            String audienceValue = request.getUserText() == null ? "" : request.getUserText().trim();
            return LlmResponse.generate(versions(request.getScene(), product, sellingPoint, audienceValue),
                    patch("audience", audienceValue));
        }
        return LlmResponse.generate(versions(request.getScene(), product, sellingPoint, audience), null);
    }

    private List<String> versions(String scene, String product, String sellingPoint, String audience) {
        String tag = scene == null || scene.isEmpty() ? "朋友圈" : scene;
        List<String> list = new ArrayList<>();
        list.add("【" + tag + " · 情感版】" + product + "，为" + audience + "而生。" + sellingPoint
                + "，是它最动人的答案。这个" + tag + "，把心意交给我们。");
        list.add("【" + tag + " · 种草版】被问爆的" + product + "来了！主打" + sellingPoint + "，"
                + audience + "闭眼入，评论区扣1安排。");
        list.add("【" + tag + " · 简洁版】" + product + "｜" + sellingPoint + "。适合" + audience + "，到店体验更优惠。");
        return list;
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
}
