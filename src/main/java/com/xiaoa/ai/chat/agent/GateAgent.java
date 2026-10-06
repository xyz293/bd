package com.xiaoa.ai.chat.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xiaoa.ai.chat.agent.skill.SkillRegistry;
import com.xiaoa.ai.chat.graph.ChatFlowState;
import com.xiaoa.ai.chat.provider.AgentPromptProperties;
import com.xiaoa.ai.chat.provider.LlmProvider;
import com.xiaoa.ai.chat.provider.LlmRequest;
import com.xiaoa.ai.chat.provider.LlmResponse;
import com.xiaoa.ai.chat.service.ChatMemoryService;
import com.xiaoa.ai.chat.service.MarketingCalendarService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ① 判断信息够不够 Agent（gateAssess 节点，HITL Gate 循环中枢）：
 * 消息进来先做一次数据装配（无上下文模式：槽位容器从零开始 + 营销日历），再调一次 LLM 同时完成
 * <b>意图识别（NEW_CREATE/REVISE/CONSULT）+ 内容形态与主题判定（contentType/theme 槽位抽取）
 * + 槽位抽取（contextPatch）+ 充分性判断（ready）</b>：
 * <ul>
 *   <li>足够（ENOUGH）→ 给出生成前需要调用的技能（needSkills），交 SkillAgent 收集知识；</li>
 *   <li>不够（NOT_ENOUGH）→ 给出缺口与候选方向，交 OptionAgent 出选项卡挂起；
 *       用户选择后经 applyOption 合并回上下文再次进入本节点复判，循环直到足够。</li>
 * </ul>
 * REVISE/CONSULT 意图不进创作循环，由图路由到生成/整合节点。轮次封顶（3 轮）后 AI 代选兜底放行。
 *
 * <p>判断权全部交给模型思考：信息够不够、缺什么、下一个问什么、代选推荐值，均由 LLM 现场判断；
 * 代码不写死槽位字典去复核/推翻模型结论，只负责编排（挂起/恢复/轮次收敛）与
 * LLM 不可用时的降级兜底（内置默认值，仅兜底不参与主链路）。</p>
 *
 * <p>隔离记忆：判断轮次（round）与每轮判定结论（lastVerdict）。</p>
 */
@Component
public class GateAgent extends BaseNodeAgent {

    /** Gate 轮次上限：复判超过 3 轮仍不够 → AI 代选兜底 */
    private static final int GATE_MAX_ROUND = 3;

    private final LlmProvider llmProvider;
    private final SkillRegistry skillRegistry;
    private final ChatMemoryService memoryService;
    private final MarketingCalendarService marketingCalendarService;
    private final ContextLoader contextLoader;

    public GateAgent(AgentMemoryService agentMemory, AgentPromptProperties prompts, LlmProvider llmProvider,
                     SkillRegistry skillRegistry, ChatMemoryService memoryService,
                     MarketingCalendarService marketingCalendarService, ContextLoader contextLoader) {
        super(agentMemory, prompts);
        this.llmProvider = llmProvider;
        this.skillRegistry = skillRegistry;
        this.memoryService = memoryService;
        this.marketingCalendarService = marketingCalendarService;
        this.contextLoader = contextLoader;
    }

    @Override
    public String name() {
        return "gateAgent";
    }

    @Override
    public String systemPrompt() {
        return prompts.getGate(); // GateAgent 专属提示词（YAML xiaoa.ai.llm.prompts.gate 可配）
    }

    @Override
    public void invoke(ChatFlowState state) {
        // ① 数据装配：先把人工输入/选项选择的累积轨迹并入上下文，再补充营销日历。
        // Gate 复判必须读取 Redis 最新轨迹，否则可能重复询问已由人工确认的内容形态/主题。
        contextLoader.load(state);
        ensureContext(state);
        applyExplicitContentType(state);

        // ② 超时兜底恢复 / 用户已选「你帮我定」：不再询问，由模型思考代选补齐后视为足够
        if (ChatFlowState.RESUME_TIMEOUT.equals(state.getResumeType()) || state.isDirectGenerate()) {
            state.setIntent(ChatFlowState.INTENT_NEW_CREATE);
            String decidedReason = aiDecide(state);
            if (state.getNeedSkills().isEmpty()) {
                state.setNeedSkills(skillRegistry.filterValid(Arrays.asList("queryProduct", "queryCalendar")));
            }
            finishVerdict(state, true, new ArrayList<String>(),
                    decidedReason == null ? "选项卡超时或用户选择你帮我定，AI 代选补齐" : decidedReason);
            memoryService.clearPending(state.getSession().getId());
            return;
        }

        // ③ 一次 LLM：意图 + 抽槽位 + 充分性判断
        int round = loadRound(state);
        boolean ready;
        List<String> missing;
        List<String> options;
        List<String> skills;
        String reason;

        String confirmedContentType = textOf(parse(state.getContextJson()), "contentType");
        try {
            LlmResponse response = llmProvider.complete(withPrompt(LlmRequest.gate(state.getSession().getScene(),
                    state.getContextJson(), "[]", state.getUserInput())));
            state.setIntent(normalizeIntent(response.getIntent()));
            ready = Boolean.TRUE.equals(response.getReady());
            missing = safeList(response.getMissing());
            options = safeList(response.getOptions());
            skills = skillRegistry.filterValid(response.getNeedSkills());
            reason = response.getReason();
            // Gate 顺带抽取的槽位补丁（含用户选项卡选择里的商品方向等）合并回上下文
            if (response.getContextPatchJson() != null && !response.getContextPatchJson().trim().isEmpty()) {
                state.setContextJson(mergeContext(state.getContextJson(), response.getContextPatchJson()));
            }
            // 模型抽槽位不能覆盖用户已确认的内容形态（比如把「文案」误改成「图片」）。
            if (confirmedContentType != null) {
                state.setContextJson(mergeContext(state.getContextJson(), patch("contentType", confirmedContentType)));
            }
        } catch (RuntimeException exception) {
            // Gate 失败兜底：按代码缺口判断（必填槽位齐 → 足够），技能默认取商品资料
            state.setIntent(ChatFlowState.INTENT_NEW_CREATE);
            ready = missingRequired(state).isEmpty();
            missing = missingRequired(state);
            options = new ArrayList<String>();
            skills = ready
                    ? skillRegistry.filterValid(Collections.singletonList("queryProduct"))
                    : new ArrayList<String>();
            reason = "gate 调用失败，按必填槽位兜底判断";
        }

        // 员工明确给出的内容形态已在上下文中确认，模型即使误报缺口也不得重复询问。
        if (textOf(parse(state.getContextJson()), "contentType") != null) {
            missing.removeIf(item -> isContentTypeGap(item));
            if (missing.isEmpty()) {
                ready = true;
                options = new ArrayList<String>();
            }
        }

        // REVISE/CONSULT 不进创作循环，直接交图路由（生成/整合），无需充分性
        if (!ChatFlowState.INTENT_NEW_CREATE.equals(state.getIntent())) {
            state.setSufficiency(ChatFlowState.SUFFICIENT_ENOUGH);
            state.setGateRound(round);
            remember(state, "round", String.valueOf(round));
            remember(state, "intent", state.getIntent());
            return;
        }

        // ④ 轮次封顶：复判仍不够 → 模型思考代选（推荐值由模型给，代码不写死默认值），保证收敛
        if (!ready && round + 1 >= GATE_MAX_ROUND) {
            String decidedReason = aiDecide(state);
            ready = true;
            missing = new ArrayList<String>();
            if (skills.isEmpty()) {
                skills = skillRegistry.filterValid(Collections.singletonList("queryProduct"));
            }
            reason = decidedReason == null ? "选项卡复判轮次已达上限，AI 代选补齐" : decidedReason;
        }

        state.setGateMissing(missing);
        state.setGateOptions(options);
        state.setNeedSkills(skills);
        state.setGateReason(reason);
        finishVerdict(state, ready, missing, reason);
        state.setGateRound(round + 1);
        remember(state, "round", String.valueOf(round + 1));
        remember(state, "intent", state.getIntent());
    }

    // ==================== 内部逻辑 ====================

    /** 数据装配（无上下文模式）：容器从零开始（不覆盖编排内已累积状态），补充营销日历。 */
    private void ensureContext(ChatFlowState state) {
        if (state.getContextJson() == null || state.getContextJson().trim().isEmpty()) {
            state.setContextJson("{}");
        }
        state.setCalendarFestivals(marketingCalendarService.upcoming30Days());
        remember(state, "calendar", jsonList(state.getCalendarFestivals()));
    }

    private boolean isContentTypeGap(String item) {
        if (item == null) {
            return false;
        }
        String normalized = item.trim().toLowerCase(java.util.Locale.ROOT);
        return "contenttype".equals(normalized) || "内容形态".equals(normalized) || "内容类型".equals(normalized);
    }

    /**
     * 把员工明确说出的内容形态直接落入确认槽位，避免每轮都要求模型从自然语言重抽一次。
     * 只在 contentType 尚未填写时处理；已回填或用户选定的结构化值优先保留。
     */
    private void applyExplicitContentType(ChatFlowState state) {
        JsonNode context = parse(state.getContextJson());
        if (textOf(context, "contentType") != null) {
            return;
        }

        String promptTrail = agentMemory.readPromptTrail(state.getThreadId());
        // 轨迹在前、本轮输入在后，确保当前明确补充优先于早先表述。
        String humanText = defaultIfBlank(promptTrail, "") + "；" + defaultIfBlank(state.getUserInput(), "");
        String normalized = humanText.toLowerCase(java.util.Locale.ROOT);
        String contentType = latestExplicitContentType(normalized);
        if (contentType != null) {
            state.setContextJson(mergeContext(state.getContextJson(), patch("contentType", contentType)));
        }
    }

    /** 识别最近一次明确表达的内容形态；「不要文案」等否定表述不视为选中文案。 */
    private String latestExplicitContentType(String text) {
        int copyIndex = text.lastIndexOf("文案");
        if (copyIndex >= 0 && isNegatedCopyType(text, copyIndex)) {
            copyIndex = -1;
        }
        copyIndex = Math.max(copyIndex, text.lastIndexOf("纯文字"));

        int imageIndex = Math.max(text.lastIndexOf("图片"), Math.max(text.lastIndexOf("做图"), text.lastIndexOf("海报")));
        int videoIndex = Math.max(text.lastIndexOf("视频"), Math.max(text.lastIndexOf("短视频"), text.lastIndexOf("拍视频")));

        if (copyIndex < 0 && imageIndex < 0 && videoIndex < 0) {
            return null;
        }
        if (videoIndex > copyIndex && videoIndex > imageIndex) {
            return "视频";
        }
        if (imageIndex > copyIndex) {
            return "图片";
        }
        return "文案";
    }

    private boolean isNegatedCopyType(String text, int copyIndex) {
        int start = Math.max(0, copyIndex - 8);
        String prefix = text.substring(start, copyIndex);
        return prefix.contains("不要") || prefix.contains("不需要") || prefix.contains("不做")
                || prefix.contains("不是") || prefix.contains("无需");
    }

    /** 必填缺口（预注册槽位字典）：内容形态/主题/商品不给没法做（选项卡按同序出题）。 */
    private List<String> missingRequired(ChatFlowState state) {
        List<String> missing = new ArrayList<String>();
        if (textOf(parse(state.getContextJson()), "contentType") == null) {
            missing.add("contentType");
        }
        if (textOf(parse(state.getContextJson()), "theme") == null) {
            missing.add("theme");
        }
        if (textOf(parse(state.getContextJson()), "product") == null) {
            missing.add("product");
        }
        return missing;
    }

    private String normalizeIntent(String intent) {
        if (ChatFlowState.INTENT_CONSULT.equals(intent) || ChatFlowState.INTENT_REVISE.equals(intent)) {
            return intent;
        }
        return ChatFlowState.INTENT_NEW_CREATE;
    }

    /** 判定结论落 state + 隔离记忆（本 Agent 的判断历史）。 */
    private void finishVerdict(ChatFlowState state, boolean ready, List<String> missing, String reason) {
        state.setSufficiency(ready ? ChatFlowState.SUFFICIENT_ENOUGH : ChatFlowState.SUFFICIENT_NOT);
        state.setGateMissing(missing);
        state.setGateReason(reason);
        try {
            Map<String, Object> verdict = new LinkedHashMap<String, Object>();
            verdict.put("round", recall(state, "round") == null ? 0 : recall(state, "round"));
            verdict.put("ready", ready);
            verdict.put("missing", missing);
            verdict.put("needSkills", state.getNeedSkills());
            verdict.put("reason", reason);
            remember(state, "lastVerdict", objectMapper.writeValueAsString(verdict));
        } catch (Exception ignored) {
            // 记忆写失败不影响主流程
        }
    }

    /** 判断轮次：隔离记忆优先，miss 回退 state（checkpoint 恢复场景）。 */
    private int loadRound(ChatFlowState state) {
        String stored = recall(state, "round");
        if (stored != null) {
            try {
                return Integer.parseInt(stored);
            } catch (NumberFormatException ignored) {
                // 记忆损坏回退 state
            }
        }
        return state.getGateRound();
    }

    /**
     * AI 代选（模型思考）：在上下文副本上追加营销日历与 directGenerate 标记后调 Gate 模型，
     * 由模型结合已确认槽位/员工输入/营销节点为缺失槽位给出推荐值（contextPatch 回填），
     * 并把新填或改写的槽位标注为 AI 代选；代码不写死推荐值，
     * 模型不可用时才降级为内置默认值（{@link #aiDecideFallback}，仅兜底不参与主链路）。
     *
     * @return 模型给出的代选理由；降级或模型未给理由时返回 null
     */
    private String aiDecide(ChatFlowState state) {
        try {
            LlmResponse response = llmProvider.complete(withPrompt(LlmRequest.gate(state.getSession().getScene(),
                    contextForAiDecide(state), "[]", state.getUserInput())));
            if (state.getNeedSkills().isEmpty()) {
                state.setNeedSkills(skillRegistry.filterValid(safeList(response.getNeedSkills())));
            }
            String patchJson = response.getContextPatchJson();
            if (patchJson != null && !patchJson.trim().isEmpty() && !"{}".equals(patchJson.trim())) {
                JsonNode before = parse(state.getContextJson());
                state.setContextJson(mergeContext(state.getContextJson(), patchJson));
                markAiDecided(state, before, patchJson);
                remember(state, "aiDecided", jsonList(state.getAiDecidedSlots()));
            }
            return response.getReason();
        } catch (RuntimeException ignored) {
            // 模型不可用（网络/解析失败）→ 降级内置默认值补齐
        }
        aiDecideFallback(state);
        return null;
    }

    /** 代选请求上下文：会话上下文副本 + 营销日历 + directGenerate 标记（仅随请求发给模型，不回写会话上下文）。 */
    private String contextForAiDecide(ChatFlowState state) {
        try {
            JsonNode base = parse(state.getContextJson());
            ObjectNode merged = objectMapper.createObjectNode();
            if (base.isObject()) {
                merged.setAll((ObjectNode) base);
            }
            if (!state.getCalendarFestivals().isEmpty()) {
                ArrayNode festivals = merged.putArray("calendarFestivals");
                state.getCalendarFestivals().forEach(festivals::add);
            }
            merged.put("directGenerate", "true");
            return objectMapper.writeValueAsString(merged);
        } catch (Exception exception) {
            return mergeContext(state.getContextJson(), patch("directGenerate", "true"));
        }
    }

    /** 标记 AI 代选槽位：代选补丁里「原值为空或被模型改写」的槽位（成品需标注「AI 代选，可改」）。 */
    private void markAiDecided(ChatFlowState state, JsonNode before, String patchJson) {
        try {
            JsonNode patch = objectMapper.readTree(patchJson);
            if (!patch.isObject()) {
                return;
            }
            List<String> slots = state.getAiDecidedSlots();
            patch.fields().forEachRemaining(field -> {
                String value = field.getValue() == null ? null : field.getValue().asText("");
                if (value == null || value.trim().isEmpty()) {
                    return; // 空值补丁不标注
                }
                String previous = before.path(field.getKey()).asText("");
                if ((previous.trim().isEmpty() || !previous.trim().equals(value.trim()))
                        && !slots.contains(field.getKey())) {
                    slots.add(field.getKey());
                }
            });
        } catch (Exception ignored) {
            // 补丁非法时跳过标注，不影响主流程
        }
    }

    /** 降级兜底（仅 LLM 不可用时）：按内置默认值补齐必填缺口，成品标注「AI 代选，可改」。 */
    private void aiDecideFallback(ChatFlowState state) {
        for (String slotKey : missingRequired(state)) {
            String defaultValue;
            if ("contentType".equals(slotKey)) {
                defaultValue = "文案";            // 兜底默认最快可交付形态
            } else if ("theme".equals(slotKey)) {
                defaultValue = "日常种草";         // 兜底默认百搭主题
            } else if ("product".equals(slotKey)) {
                defaultValue = "本店最近热销款";
            } else if ("platform".equals(slotKey)) {
                defaultValue = "朋友圈";
            } else {
                defaultValue = "AI 推荐";
            }
            state.setContextJson(mergeContext(state.getContextJson(), patch(slotKey, defaultValue)));
            state.getAiDecidedSlots().add(slotKey);
        }
        remember(state, "aiDecided", jsonList(state.getAiDecidedSlots()));
    }
}
