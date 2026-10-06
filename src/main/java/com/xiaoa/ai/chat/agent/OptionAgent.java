package com.xiaoa.ai.chat.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xiaoa.ai.chat.dto.OptionCardVO;
import com.xiaoa.ai.chat.dto.PendingOption;
import com.xiaoa.ai.chat.dto.QOptionVO;
import com.xiaoa.ai.chat.graph.ChatFlowState;
import com.xiaoa.ai.chat.provider.AgentPromptProperties;
import com.xiaoa.ai.chat.provider.LlmProvider;
import com.xiaoa.ai.chat.provider.LlmRequest;
import com.xiaoa.ai.chat.provider.LlmResponse;
import com.xiaoa.ai.chat.service.ChatMemoryService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 选项卡 Agent（presentOptionCard / applyOption 节点，出题对齐创作顾问人设）：
 * Gate 判定信息不足时挂起选项卡（前端 30s 倒计时，后端 60s 兜底扫描）。
 *
 * <p>出题主链路交给 LLM 思考：问哪个维度、题干怎么写、每条选项的 label/hint/slotKey
 * 全部由模型根据「已确认槽位 + 已问维度 + Gate 缺口 + 员工输入」现场生成，代码不写死出题表；
 * Agent 只负责 LLM 输出的规范化（key 重编 A/B/C、过滤无效项、追加「你帮我定」D 项）、
 * 挂起/恢复编排，以及 LLM 失败时的候选兜底。</p>
 *
 * <p>隔离记忆：出过的选项（shownOptions）、已问维度（askedDimensions）、用户最近选择（lastChoice）。
 * askedDimensions 按任务存（threadId=任务 ID），新任务首轮出卡时重置，保证每次创作独立。</p>
 */
@Component
public class OptionAgent extends BaseNodeAgent {

    /** 选项卡前端倒计时秒数 */
    private static final int OPTION_CARD_DEADLINE_SECONDS = 30;

    /** LLM 出题失败兜底时的槽位 key 展示名（仅降级文案，不参与主链路判断） */
    private static final Map<String, String> SLOT_LABELS = createSlotLabels();

    private static Map<String, String> createSlotLabels() {
        Map<String, String> labels = new LinkedHashMap<String, String>();
        labels.put("contentType", "内容形态");
        labels.put("theme", "主题方向");
        labels.put("platform", "发布平台");
        labels.put("scene", "内容目的");
        labels.put("product", "商品素材");
        labels.put("festival", "节日节点");
        labels.put("style", "风格");
        return labels;
    }

    private final ChatMemoryService memoryService;
    private final ContextLoader contextLoader;
    private final LlmProvider llmProvider;

    public OptionAgent(AgentMemoryService agentMemory, AgentPromptProperties prompts, ChatMemoryService memoryService,
                       ContextLoader contextLoader, LlmProvider llmProvider) {
        super(agentMemory, prompts);
        this.memoryService = memoryService;
        this.contextLoader = contextLoader;
        this.llmProvider = llmProvider;
    }

    @Override
    public String name() {
        return "optionAgent";
    }

    @Override
    public String systemPrompt() {
        return prompts.getOptions(); // OptionAgent 专属提示词（YAML xiaoa.ai.llm.prompts.options 可配）
    }

    /** 默认入口 = 出卡挂起（图上按 present/applyChoice 两个节点引用）。 */
    @Override
    public void invoke(ChatFlowState state) {
        present(state);
    }

    // ==================== 出卡 + 挂起（presentOptionCard 节点） ====================

    public void present(ChatFlowState state) {
        contextLoader.load(state);
        // 出题主链路：一次 LLM 调用同时拿题干与选项（维度/题干/选项全由模型现场决定）
        LlmResponse card = llmCard(state);
        String question = card == null ? null : card.getQuestion();
        List<QOptionVO> options = card == null ? null : card.getCardOptions();
        if (options == null || options.isEmpty()) {
            // LLM 出题失败/未给出有效选项 → 用 Gate 候选与缺口兜底（题干同步兜底）
            options = fallbackOptions(state);
            question = null;
        }
        options.add(aiDecideOption());
        state.setOptionCard(new OptionCardVO(options, OPTION_CARD_DEADLINE_SECONDS));
        state.setReplyAction(ChatFlowState.REPLY_OPTION_CARD);
        state.setQuestion(tailored(question, state));
        holdPendingOption(state);
        // 隔离记忆：记录本轮出卡内容与 LLM 出题涉及的维度（供下一轮出卡去重）
        remember(state, "shownOptions", safeJson(options));
        rememberAsked(state, options);
    }

    /**
     * 出题主链路：调 LLM 思考生成选项卡（模型自己决定问哪个维度、题干与选项）。
     * 输入 = 生效上下文 + 已问维度 + Gate 缺口 + 员工本轮输入；失败返回 null 交由兜底。
     */
    private LlmResponse llmCard(ChatFlowState state) {
        Set<String> asked = askedDimensions(state);
        if (state.getGateRound() == 0) {
            asked.clear(); // 新任务首轮：每次创作独立，不继承上一任务的已问维度
            remember(state, "askedDimensions", jsonList(new ArrayList<String>(asked)));
        }
        addConfirmedDimensions(state, asked);
        try {
            LlmResponse response = llmProvider.complete(withPrompt(LlmRequest.options(
                    state.getSession().getScene(),
                    contextForOptions(state, asked),
                    jsonList(state.getGateMissing()),
                    state.getUserInput())));
            if (response == null || response.getCardOptions() == null) {
                return null;
            }
            List<QOptionVO> options = new ArrayList<QOptionVO>();
            for (QOptionVO option : response.getCardOptions()) {
                if (option != null && option.getLabel() != null && !option.getLabel().trim().isEmpty()
                        && !"D".equals(option.getKey()) && !isAlreadyConfirmed(state, option.getSlotKey())) {
                    options.add(option); // D 项由 Agent 统一追加；已确认槽位不重复出题
                }
            }
            if (options.isEmpty()) {
                return null;
            }
            if (options.size() > 3) {
                options.subList(3, options.size()).clear(); // 最多 3 个业务选项，D 永远 = 你帮我定
            }
            String[] keys = {"A", "B", "C"};
            for (int i = 0; i < options.size(); i++) {
                options.get(i).setKey(keys[i]); // 统一重编 key，模型输出 key 仅作参考
            }
            // 返回过滤后的选项；否则下游仍读取 response 原始列表，已确认槽位过滤会失效。
            response.setCardOptions(options);
            return response;
        } catch (RuntimeException ignored) {
            // LLM 出题失败（网络/解析等）→ 模板兜底
            return null;
        }
    }

    /** 将上下文已有的决策槽位标记为已确认，避免模型漏看时再次出题。 */
    private void addConfirmedDimensions(ChatFlowState state, Set<String> asked) {
        JsonNode context = parse(state.getContextJson());
        for (String dimension : new String[] {"contentType", "theme", "platform", "scene", "product", "festival", "style"}) {
            if (textOf(context, dimension) != null) {
                asked.add(dimension);
            }
        }
    }

    /** 已确认槽位的选项由后端过滤，不依赖模型遵守防重复提示。 */
    private boolean isAlreadyConfirmed(ChatFlowState state, String slotKey) {
        return slotKey != null && textOf(parse(state.getContextJson()), slotKey) != null;
    }

    /** LLM 出题时给模型的上下文：已确认槽位 + 已问维度（让模型自己避开重复问）。 */
    private String contextForOptions(ChatFlowState state, Set<String> asked) {
        try {
            JsonNode context = parse(state.getContextJson());
            if (!context.isObject()) {
                context = objectMapper.createObjectNode();
            }
            ObjectNode merged = objectMapper.createObjectNode();
            merged.setAll((ObjectNode) context);
            ArrayNode askedNode = merged.putArray("askedDimensions");
            for (String dimension : asked) {
                askedNode.add(dimension);
            }
            return objectMapper.writeValueAsString(merged);
        } catch (Exception exception) {
            return state.getContextJson();
        }
    }

    /** 兜底出卡（仅 LLM 不可用时）：Gate 候选方向 / 缺口槽位 key 转展示名。 */
    private List<QOptionVO> fallbackOptions(ChatFlowState state) {
        List<String> candidates = new ArrayList<String>(state.getGateOptions());
        if (candidates.isEmpty()) {
            for (String gap : state.getGateMissing()) {
                if (isAlreadyConfirmed(state, gap)) {
                    continue;
                }
                String label = SLOT_LABELS.getOrDefault(gap, gap);
                candidates.add(label);
            }
        }
        if (candidates.isEmpty()) {
            candidates.add("我补充一下关键信息");
        }
        List<QOptionVO> options = new ArrayList<QOptionVO>();
        String[] keys = {"A", "B", "C"};
        for (int i = 0; i < Math.min(3, candidates.size()); i++) {
            options.add(new QOptionVO(keys[i], candidates.get(i), "点选后按这个方向继续", null));
        }
        return options;
    }

    /** D 选项永远存在：「你帮我定」= AI 代选并标注，之后可改。 */
    private QOptionVO aiDecideOption() {
        return new QOptionVO("D", "你帮我定", "AI 按推荐代选并标注，生成后可再改");
    }

    /** 题干：优先用 LLM 生成的，缺失时兜底为通用引导语；统一追加倒计时与轮次提示。 */
    private String tailored(String llmQuestion, ChatFlowState state) {
        String topic = (llmQuestion == null || llmQuestion.trim().isEmpty())
                ? "选一个方向继续～" : llmQuestion.trim();
        return topic + " 选一个或直接在输入框补充；30 秒不选则按「你帮我定」执行（第 " + state.getGateRound() + " 轮）";
    }

    /** 已问维度（隔离记忆）：同一任务内跨多轮复判去重。 */
    private Set<String> askedDimensions(ChatFlowState state) {
        Set<String> asked = new LinkedHashSet<String>();
        String stored = recall(state, "askedDimensions");
        if (stored != null) {
            try {
                JsonNode array = objectMapper.readTree(stored);
                if (array.isArray()) {
                    array.forEach(item -> asked.add(item.asText("")));
                }
            } catch (Exception ignored) {
                // 记忆损坏按空处理
            }
        }
        return asked;
    }

    /** 记录本次出卡涉及的维度（按选项 slotKey 取并集）。 */
    private void rememberAsked(ChatFlowState state, List<QOptionVO> options) {
        Set<String> asked = askedDimensions(state);
        for (QOptionVO option : options) {
            if (option != null && option.getSlotKey() != null && !option.getSlotKey().trim().isEmpty()) {
                asked.add(option.getSlotKey().trim());
            }
        }
        remember(state, "askedDimensions", jsonList(new ArrayList<String>(asked)));
    }

    /** 挂起载荷：写恢复身份与截止时间（供 /chat/option 恢复与超时兜底扫描）。 */
    private void holdPendingOption(ChatFlowState state) {
        PendingOption pending = new PendingOption();
        pending.setTenantId(state.tenantId());
        pending.setUserId(state.getPrincipal().getUserId());
        pending.setOrgId(state.getPrincipal().getOrgId());
        pending.setRole(state.getPrincipal().getRole());
        pending.setDataScope(state.getPrincipal().getDataScope());
        pending.setSessionId(state.getSession().getId());
        pending.setDeadline(System.currentTimeMillis() + OPTION_CARD_DEADLINE_SECONDS * 1000L);
        memoryService.holdPendingOption(pending);
        memoryService.markPending(state.getSession().getId(), "OPTION_CARD");
        memoryService.track(state.tenantId(), "option_card_shown");
    }

    private String safeJson(List<QOptionVO> options) {
        try {
            return objectMapper.writeValueAsString(options);
        } catch (Exception exception) {
            return "[]";
        }
    }

    // ==================== 用户选择恢复（applyOption 节点） ====================

    /**
     * 应用用户选择后回 Gate 复判（循环）：
     * A/B/C → 选择回填对应槽位（LLM 生成的选项自带 slotKey）+ clarification 文本轨迹累积；
     * D → 授权 AI 代选（directGenerate），Gate 直接放行（代选槽位会标注，成稿可改）。
     */
    public void applyChoice(ChatFlowState state) {
        contextLoader.load(state);
        String key = state.getOptionKey();
        QOptionVO chosen = resolveOption(state, key);
        if ("D".equals(key)) {
            // 用户选「你帮我定」：授权 AI 代选补齐，下轮 Gate 不再询问
            state.setDirectGenerate(true);
        } else if (chosen != null) {
            // 选择落槽位：Gate 复判「已有值不重复问」，下一维度才出题
            if (chosen.getSlotKey() != null) {
                state.setContextJson(mergeContext(state.getContextJson(), patch(chosen.getSlotKey(), chosen.getLabel())));
            }
            // clarification 文本轨迹保留（LLM 可读的自然语言累积）
            String current = textOf(parse(state.getContextJson()), "clarification");
            String merged = isBlank(current) ? chosen.getLabel() : current + "；" + chosen.getLabel();
            state.setContextJson(mergeContext(state.getContextJson(), patch("clarification", merged)));
        }
        memoryService.clearPending(state.getSession().getId());
        memoryService.track(state.tenantId(), "option_card_" + (key == null ? "D" : key));
        // 隔离记忆：用户最近选择
        remember(state, "lastChoice", key + ":" + (chosen == null ? "null" : chosen.getLabel()));
        // 人工上下文提示词轨迹：选项选择拼接进 Redis 任务记忆（flow 命名空间），
        // 后续每轮 LLM 调用经 ContextLoader 并入 context.contextTrail 都能看到累积的人工更新
        String trail = "D".equals(key) ? "员工选了「你帮我定」（授权AI代选补齐）"
                : (chosen == null ? null : "员工选了「" + chosen.getLabel() + "」"
                        + (isBlank(chosen.getSlotKey()) ? "" : "（" + chosen.getSlotKey() + "）"));
        agentMemory.appendPromptTrail(state.getThreadId(), trail);
    }

    /** 从 checkpoint 恢复的选项卡里反查用户所选项。 */
    private QOptionVO resolveOption(ChatFlowState state, String key) {
        if (state.getOptionCard() == null || state.getOptionCard().getOptions() == null || key == null) {
            return null;
        }
        for (QOptionVO option : state.getOptionCard().getOptions()) {
            if (option != null && key.equals(option.getKey())) {
                return option;
            }
        }
        return null;
    }
}
