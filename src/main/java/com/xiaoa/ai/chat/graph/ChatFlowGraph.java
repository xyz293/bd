package com.xiaoa.ai.chat.graph;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoa.admin.service.ComplianceService;
import com.xiaoa.ai.chat.dto.ChatReplyVO;
import com.xiaoa.ai.chat.dto.OptionCardVO;
import com.xiaoa.ai.chat.dto.PendingOption;
import com.xiaoa.ai.chat.dto.QOptionVO;
import com.xiaoa.ai.chat.dto.QuestionVO;
import com.xiaoa.ai.chat.mapper.ChatMessageMapper;
import com.xiaoa.ai.chat.mapper.ChatSessionMapper;
import com.xiaoa.ai.chat.model.ChatMessage;
import com.xiaoa.ai.chat.model.ChatSession;
import com.xiaoa.ai.chat.provider.LlmProvider;
import com.xiaoa.ai.chat.provider.LlmRequest;
import com.xiaoa.ai.chat.provider.LlmResponse;
import com.xiaoa.ai.chat.service.ChatBillingService;
import com.xiaoa.ai.chat.service.ChatMemoryService;
import com.xiaoa.ai.chat.service.MarketingCalendarService;
import com.xiaoa.ai.graph.CompiledGraph;
import com.xiaoa.ai.graph.NodeListener;
import com.xiaoa.ai.graph.StateGraph;
import com.xiaoa.ai.dto.GenerateRequest;
import com.xiaoa.ai.model.Work;
import com.xiaoa.ai.service.AiGatewayService;
import com.xiaoa.ai.chat.provider.DemoLlmProvider;
import com.xiaoa.common.exception.BusinessException;
import com.xiaoa.common.exception.ErrorCode;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 珠宝门店 AI 创作顾问 · 对话创作状态图（节点编排定稿方案实现）。
 * 7 个节点、串行为主、一处循环（③→②，问卷轮次 ≤2）、三类挂起（问卷/选项卡/视频异步）。
 *
 * <p>图结构（条件边用 ‹›标注，(hold) 为挂起点，恢复通过 resumeType 重进图）：</p>
 *
 * <pre>
 * START ‹resume›──NEW──> ① fetchContext ──> ② understandIntent ‹intent›
 *   │                                          ├─ CONSULT ──────────> respondConsult ──────────────┐
 *   │                                          ├─ REVISE ──> reviseValidate ─> reviseCallLlm ─> reviseCompliance ─> reviseBilling ─┤
 *   │                                          └─ NEW_CREATE ‹missing›                                              │
 *   │                                               ├─ 缺 &amp; 轮&lt;2 ──> ③ composeQuestionnaire ─> holdQuestionnaire(hold1) ─┤
 *   │                                               ├─ 缺 &amp; 轮≥2 ──> aiDecideSlots ──┐                                    │
 *   │                                               └─ 信息齐 ────────┐               │                                    │
 *   │                                                                 └─> ④ verifyAndCharge ‹quota›   │                                    │
 *   │                                                                        ├─ OK ──> ⑤ composeOptionCard ‹候选›        │
 *   │                                                                        │             ├─ 有候选 ─> holdOptionCard(hold2) ┤
 *   │                                                                        │             └─ 无候选 ──────────┐            │
 *   │                                                                        └─ 不足 ──> respondQuota ─────────┼────────────┤
 *   │                                                                                                          v            │
 *   │                                                        ⑥ generateContent ‹taskType›                      │            │
 *   │                                                             ├─ COPY ──> reviewAndRespond ─> billingConfirm ┤            │
 *   │                                                             └─ MEDIA ──> mediaSubmit(hold3 异步) ──────────┼────────────┤
 *   │                                                                                                            v            v
 *   └──ANSWER──> mergeAnswers ──(回②)──┐                                                       responseComposer ─> persist ─> END
 *      └──OPTION──> applyOption ───────┴──────────────────────────────────────> ⑥ generateContent…
 *      └──TIMEOUT──（默认D直接生成）────────────────────────────────────────> ⑥ generateContent…
 * </pre>
 *
 * <p>事务语义：事务边界在 {@code ChatFlowService}（@Transactional）。预扣（hold）在同一事务内，
 * 任何异常整体回滚即自动释放额度；confirm 为资金无操作（预扣即扣费）。</p>
 */
@Component
public class ChatFlowGraph {

    private static final String FALLBACK_QUESTION = "能再具体一点吗？";
    /** 携带给 LLM 的最近消息条数（10 轮 = 20 条） */
    private static final int HISTORY_MESSAGES = 20;
    /** 问卷轮次上限（两轮后仍缺 → AI 代选） */
    private static final int QUESTIONNAIRE_MAX_ROUND = 2;
    /** 选项卡前端倒计时秒数 */
    private static final int OPTION_CARD_DEADLINE_SECONDS = 30;
    /** 必填槽位（方案 ②：platform 等可 AI 代选，仅商品为「不给没法做」） */
    private static final List<String> REQUIRED_SLOTS = Collections.singletonList("product");

    private final ChatSessionMapper sessionMapper;
    private final ChatMessageMapper messageMapper;
    private final ChatBillingService billingService;
    private final ComplianceService complianceService;
    private final ChatMemoryService memoryService;
    private final MarketingCalendarService marketingCalendarService;
    private final AiGatewayService aiGatewayService;
    private final LlmProvider llmProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final CompiledGraph<ChatFlowState> graph;

    public ChatFlowGraph(ChatSessionMapper sessionMapper, ChatMessageMapper messageMapper,
                         ChatBillingService billingService, ComplianceService complianceService,
                         ChatMemoryService memoryService, MarketingCalendarService marketingCalendarService,
                         AiGatewayService aiGatewayService, LlmProvider llmProvider) {
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.billingService = billingService;
        this.complianceService = complianceService;
        this.memoryService = memoryService;
        this.marketingCalendarService = marketingCalendarService;
        this.aiGatewayService = aiGatewayService;
        this.llmProvider = llmProvider;
        this.graph = buildGraph();
    }

    /** 执行整张对话流程图，返回最终状态（reply 挂在 state 上）。 */
    public @NonNull ChatFlowState run(@NonNull ChatFlowState state) {
        return graph.invoke(state);
    }

    /** 执行整张图并回调节点监听器（对齐 LangGraph stream 模式，供 WebSocket 阶段进度推送）。 */
    public @NonNull ChatFlowState run(@NonNull ChatFlowState state, NodeListener<ChatFlowState> listener) {
        return graph.invoke(state, listener);
    }

    private CompiledGraph<ChatFlowState> buildGraph() {
        return new StateGraph<ChatFlowState>()
                .addNode("fetchContext", this::fetchContext)
                .addNode("understandIntent", this::understandIntent)
                .addNode("composeQuestionnaire", this::composeQuestionnaire)
                .addNode("holdQuestionnaire", this::holdQuestionnaire)
                .addNode("mergeAnswers", this::mergeAnswers)
                .addNode("aiDecideSlots", this::aiDecideSlots)
                .addNode("verifyAndCharge", this::verifyAndCharge)
                .addNode("composeOptionCard", this::composeOptionCard)
                .addNode("holdOptionCard", this::holdOptionCard)
                .addNode("applyOption", this::applyOption)
                .addNode("generateContent", this::generateContent)
                .addNode("mediaSubmit", this::mediaSubmit)
                .addNode("reviewAndRespond", this::reviewAndRespond)
                .addNode("billingConfirm", this::billingConfirm)
                .addNode("respondConsult", this::respondConsult)
                .addNode("respondQuota", this::respondQuota)
                .addNode("reviseValidate", this::reviseValidate)
                .addNode("reviseCallLlm", this::reviseCallLlm)
                .addNode("reviseCompliance", this::reviseCompliance)
                .addNode("reviseBilling", this::reviseBilling)
                .addNode("responseComposer", this::responseComposer)
                .addNode("persist", this::persist)
                // START 路由：正常新消息 / 三类挂起恢复（问卷作答、选项卡选择、选项卡超时兜底）
                .addConditionalEdges(StateGraph.START, this::resumeRouter,
                        branches(ChatFlowState.RESUME_NEW, "fetchContext",
                                ChatFlowState.RESUME_ANSWER, "mergeAnswers",
                                ChatFlowState.RESUME_OPTION, "applyOption",
                                ChatFlowState.RESUME_TIMEOUT, "generateContent"))
                .addEdge("fetchContext", "understandIntent")
                .addConditionalEdges("understandIntent", this::routeAfterIntent,
                        branches(ChatFlowState.INTENT_CONSULT, "respondConsult",
                                ChatFlowState.INTENT_REVISE, "reviseValidate",
                                "ASK_QUESTIONNAIRE", "composeQuestionnaire",
                                "AI_DECIDE", "aiDecideSlots",
                                "READY", "verifyAndCharge"))
                .addEdge("composeQuestionnaire", "holdQuestionnaire")
                .addEdge("holdQuestionnaire", "responseComposer")
                .addEdge("mergeAnswers", "understandIntent")
                .addEdge("aiDecideSlots", "verifyAndCharge")
                .addConditionalEdges("verifyAndCharge",
                        state -> state.isQuotaOk() ? "ok" : "short",
                        branches("ok", "composeOptionCard", "short", "respondQuota"))
                .addConditionalEdges("composeOptionCard",
                        state -> state.getEnhancementCandidates().isEmpty() ? "direct" : "confirm",
                        branches("direct", "generateContent", "confirm", "holdOptionCard"))
                .addEdge("holdOptionCard", "responseComposer")
                .addEdge("applyOption", "generateContent")
                .addConditionalEdges("generateContent",
                        state -> ChatFlowState.TASK_COPY.equals(state.getTaskType()) ? "copy" : "media",
                        branches("copy", "reviewAndRespond", "media", "mediaSubmit"))
                .addEdge("mediaSubmit", "responseComposer")
                .addEdge("reviewAndRespond", "billingConfirm")
                .addEdge("billingConfirm", "responseComposer")
                .addEdge("respondConsult", "responseComposer")
                .addEdge("respondQuota", "responseComposer")
                .addEdge("reviseValidate", "reviseCallLlm")
                .addEdge("reviseCallLlm", "reviseCompliance")
                .addEdge("reviseCompliance", "reviseBilling")
                .addEdge("reviseBilling", "responseComposer")
                .addEdge("responseComposer", "persist")
                .addEdge("persist", StateGraph.END)
                .compile();
    }

    // ==================== START 路由（恢复门） ====================

    /** 微调请求优先；否则按恢复类型路由（问卷作答 / 选项卡选择 / 超时兜底 / 正常新消息）。 */
    private String resumeRouter(ChatFlowState state) {
        if (state.getMode() == ChatFlowState.Mode.REVISE) {
            return ChatFlowState.INTENT_REVISE;
        }
        String resumeType = state.getResumeType();
        if (ChatFlowState.RESUME_ANSWER.equals(resumeType) || ChatFlowState.RESUME_OPTION.equals(resumeType)
                || ChatFlowState.RESUME_TIMEOUT.equals(resumeType)) {
            return resumeType;
        }
        return ChatFlowState.RESUME_NEW;
    }

    // ==================== ① fetch_context 获取数据（工具，无 LLM） ====================

    /** 一次取齐：会话记忆（Redis，miss 回退 MySQL）、历史、营销日历；后续节点只读不再查。 */
    private void fetchContext(ChatFlowState state) {
        loadContext(state);
        state.setCalendarFestivals(marketingCalendarService.upcoming30Days());
    }

    /** 上下文装配：Redis 记忆优先（断点续聊），miss 回退 MySQL 会话槽位快照。 */
    private void loadContext(ChatFlowState state) {
        ChatSession session = state.getSession();
        String slots = memoryService.loadSlots(session.getId());
        state.setContextJson(slots != null ? slots : defaultContext(session));
        state.setQuestionnaireRound(memoryService.loadQuestionnaireRound(session.getId()));
        List<ChatMessage> recent = messageMapper.findRecent(session.getId(), HISTORY_MESSAGES);
        Collections.reverse(recent);
        state.setHistoryJson(historyJson(recent));
    }

    private String defaultContext(ChatSession session) {
        return session.getContext() == null || session.getContext().trim().isEmpty()
                ? "{}" : session.getContext();
    }

    // ==================== ② understand_intent 理解意图+抽信息（LLM） ====================

    /**
     * 一次调用完成意图三分类 + 槽位抽取；缺口与增益候选由代码按预注册槽位字典计算
     * （LLM 不得发明槽位 key）。LLM 失败时兜底为新创作+文案，保证链路可用。
     */
    private void understandIntent(ChatFlowState state) {
        try {
            LlmResponse response = llmProvider.complete(LlmRequest.intent(state.getSession().getScene(),
                    state.getContextJson(), state.getHistoryJson(), state.getUserInput()));
            state.setIntent(normalizeIntent(response.getIntent()));
            state.setTaskType(normalizeTaskType(response.getTaskType()));
            state.setContextJson(mergeContext(state.getContextJson(), response.getContextPatchJson()));
        } catch (RuntimeException exception) {
            state.setIntent(ChatFlowState.INTENT_NEW_CREATE);
            state.setTaskType(ChatFlowState.TASK_COPY);
        }
        state.setMissingRequired(computeMissing(state.getContextJson()));
        state.setEnhancementCandidates(computeEnhancements(state));
    }

    private String normalizeIntent(String intent) {
        if (ChatFlowState.INTENT_CONSULT.equals(intent) || ChatFlowState.INTENT_REVISE.equals(intent)) {
            return intent;
        }
        return ChatFlowState.INTENT_NEW_CREATE;
    }

    /**
     * ② 节点条件路由（对齐方案 §3 编排）：
     * 咨询 → ⑦ 直接回复；微调 → 改写链路；新创作按缺口/轮次三向：出问卷 / AI 代选 / 直进④。
     */
    private String routeAfterIntent(ChatFlowState state) {
        String intent = state.getIntent();
        if (ChatFlowState.INTENT_CONSULT.equals(intent) || ChatFlowState.INTENT_REVISE.equals(intent)) {
            return intent;
        }
        if (!state.getMissingRequired().isEmpty()) {
            // 两轮问卷仍缺 → AI 代选默认值（成品标注），不再循环
            return state.getQuestionnaireRound() < QUESTIONNAIRE_MAX_ROUND ? "ASK_QUESTIONNAIRE" : "AI_DECIDE";
        }
        return "READY";
    }

    private String normalizeTaskType(String taskType) {
        if (ChatFlowState.TASK_IMAGE.equals(taskType) || ChatFlowState.TASK_VIDEO.equals(taskType)) {
            return taskType;
        }
        return ChatFlowState.TASK_COPY;
    }

    /** 缺口计算（代码管不失控）：预注册必填项逐一比对槽位快照。 */
    private List<String> computeMissing(String contextJson) {
        JsonNode context = parse(contextJson);
        List<String> missing = new ArrayList<String>();
        for (String required : REQUIRED_SLOTS) {
            if (isBlank(textOf(context, required))) {
                missing.add(required);
            }
        }
        return missing;
    }

    /** 增益候选（方案 ⑤）：日历有近节点且未定 festival / tone 未定 / versions 未定。 */
    private List<String> computeEnhancements(ChatFlowState state) {
        JsonNode context = parse(state.getContextJson());
        List<String> candidates = new ArrayList<String>();
        if (!state.getCalendarFestivals().isEmpty() && isBlank(textOf(context, "festival"))) {
            candidates.add("festival");
        }
        if (isBlank(textOf(context, "tone"))) {
            candidates.add("tone");
        }
        if (isBlank(textOf(context, "versions"))) {
            candidates.add("versions");
        }
        return candidates;
    }

    // ==================== ③ compose_questionnaire 生成问卷（LLM + 固定兜底） ====================

    /**
     * 针对 missing 动态出题（每轮 ≤3 题、每题 2~4 选项）；LLM 失败/超时/出题越权
     * （slotKey 不在 missing 内）时降级固定兜底问卷。
     */
    private void composeQuestionnaire(ChatFlowState state) {
        List<QuestionVO> questions = null;
        try {
            LlmResponse response = llmProvider.complete(LlmRequest.questionnaire(state.getSession().getScene(),
                    state.getContextJson(), jsonList(state.getMissingRequired())));
            questions = response.getQuestions();
        } catch (RuntimeException exception) {
            questions = null;
        }
        questions = sanitizeQuestions(questions, state.getMissingRequired());
        if (questions.isEmpty()) {
            questions = DemoLlmProvider.buildQuestions(state.getMissingRequired());
        }
        state.setPendingQuestions(questions);
    }

    /** 校验 LLM 问卷：只保留 slotKey 命中本轮缺口的题目（答案合并才有意义）。 */
    private List<QuestionVO> sanitizeQuestions(List<QuestionVO> questions, List<String> missing) {
        List<QuestionVO> valid = new ArrayList<QuestionVO>();
        if (questions == null) {
            return valid;
        }
        for (QuestionVO question : questions) {
            if (question.getSlotKey() != null && missing.contains(question.getSlotKey())
                    && question.getOptions() != null && question.getOptions().size() >= 2) {
                valid.add(question);
            }
            if (valid.size() >= 3) {
                break;
            }
        }
        return valid;
    }

    /** 挂起点1（问卷作答）：记录挂起类型，用户可随时回（会话 TTL 2h 内），无超时强制放行。 */
    private void holdQuestionnaire(ChatFlowState state) {
        memoryService.markPending(state.getSession().getId(), "QUESTIONNAIRE");
        state.setReplyAction(ChatFlowState.REPLY_QUESTIONNAIRE);
        int round = state.getQuestionnaireRound() + 1;
        state.setQuestion("先确认 " + state.getPendingQuestions().size() + " 个信息（第 " + round + "/"
                + QUESTIONNAIRE_MAX_ROUND + " 轮），选完就能开始生成～");
    }

    // ==================== 问卷恢复：答案合并 → 回 ② 重算缺口 ====================

    /** 用户作答提交：合并答案进槽位、轮次 +1、清除挂起，随后由条件边回 ②。 */
    private void mergeAnswers(ChatFlowState state) {
        ChatSession session = state.getSession();
        loadContext(state);
        if (!state.getAnswers().isEmpty()) {
            try {
                String patch = objectMapper.writeValueAsString(new LinkedHashMap<String, String>(state.getAnswers()));
                state.setContextJson(mergeContext(state.getContextJson(), patch));
            } catch (Exception ignored) {
                // 合并失败保留原槽位
            }
            memoryService.track(state.tenantId(), "questionnaire_answer");
        }
        int round = memoryService.loadQuestionnaireRound(session.getId()) + 1;
        state.setQuestionnaireRound(round);
        memoryService.saveQuestionnaireRound(session.getId(), round);
        memoryService.clearPending(session.getId());
    }

    // ==================== 问卷轮次耗尽：AI 代选默认值（成品标注「AI 代选，可改」） ====================

    private void aiDecideSlots(ChatFlowState state) {
        for (String slotKey : state.getMissingRequired()) {
            String defaultValue;
            if ("product".equals(slotKey)) {
                defaultValue = "本店最近热销款";
            } else if ("platform".equals(slotKey)) {
                defaultValue = "朋友圈";
            } else {
                defaultValue = "AI 推荐";
            }
            state.setContextJson(mergeContext(state.getContextJson(), patch(slotKey, defaultValue)));
            state.getAiDecidedSlots().add(slotKey);
        }
    }

    // ==================== ④ verify_and_charge 核验+扣额度（工具） ====================

    /**
     * 事实核验 + 额度预扣（同一事务内顺序执行；核验不阻断，额度不足拦截）：
     * <ul>
     *   <li>事实核验：槽位内字段视为已确认资料；材质/价格缺失列入 to_verify，生成时占位符</li>
     *   <li>额度预扣（hold）：仅 COPY 任务走三态；图/视频由 AI 网关内部扣费（gen:{workId}）</li>
     * </ul>
     */
    private void verifyAndCharge(ChatFlowState state) {
        verifyFacts(state);
        if (ChatFlowState.TASK_COPY.equals(state.getTaskType())) {
            state.setQuotaNeed(billingService.getCopyCost());
            String holdKey = "chat:" + state.getSession().getId() + ":hold:"
                    + memoryService.nextSeq(state.getSession().getId());
            try {
                billingService.checkAndHold(state.getPrincipal(), holdKey);
                state.setHoldKey(holdKey);
                state.setQuotaOk(true);
            } catch (BusinessException exception) {
                if (exception.getCode() == ErrorCode.QUOTA_NOT_ENOUGH.getCode()) {
                    state.setQuotaOk(false);
                } else {
                    throw exception;
                }
            }
        } else {
            state.setQuotaOk(true);
        }
    }

    /** 事实核验（不臆造）：核验通过的进 confirmed，资料缺失的进 to_verify（强制占位符）。 */
    private void verifyFacts(ChatFlowState state) {
        FactReport report = state.getFactReport();
        JsonNode context = parse(state.getContextJson());
        boolean productAiDecided = state.getAiDecidedSlots().contains("product");
        String product = textOf(context, "product");
        report.addConfirmed("商品：" + (product == null ? "未指定" : product) + (productAiDecided ? "（AI 代选，可改）" : ""));
        String platform = defaultIfBlank(textOf(context, "platform"),
                defaultIfBlank(state.getSession().getScene(), "朋友圈"));
        report.addConfirmed("平台：" + platform);
        String festival = textOf(context, "festival");
        if (!isBlank(festival)) {
            report.addConfirmed("节日：" + festival);
        }
        // 商品资料缺失字段 → 占位符（发布前请补真实信息）
        if (isBlank(textOf(context, "price"))) {
            report.addTodoVerify("价格");
        }
        if (isBlank(textOf(context, "material"))) {
            report.addTodoVerify("材质");
        }
    }

    /** 额度不足：终止本流程，提示充值（企业版引导找店长）。 */
    private void respondQuota(ChatFlowState state) {
        state.setReplyAction(ChatFlowState.REPLY_ASK);
        state.setQuestion("当前额度不足（本次创作约需 " + state.getQuotaNeed()
                + " 点），请联系店长或管理员充值后再试～");
    }

    // ==================== ⑤ option_card 生成前选项确认（规则，一次不循环） ====================

    /** 按 enhancement_candidates 生成 A/B/C/D 增益选项；已定槽位不出；D 永远存在。 */
    private void composeOptionCard(ChatFlowState state) {
        List<QOptionVO> options = new ArrayList<QOptionVO>();
        if (!state.getEnhancementCandidates().isEmpty()) {
            String nearestFestival = marketingCalendarService.nearest();
            for (String candidate : state.getEnhancementCandidates()) {
                if ("festival".equals(candidate)) {
                    options.add(new QOptionVO("A", "加节日氛围",
                            nearestFestival == null ? "注入节日元素" : nearestFestival + "，注入节日元素"));
                } else if ("tone".equals(candidate)) {
                    options.add(new QOptionVO("B", "换个语气", "更口语 / 更正式"));
                } else if ("versions".equals(candidate)) {
                    options.add(new QOptionVO("C", "多备一版", "3 版变 4 版"));
                }
            }
        }
        options.add(new QOptionVO("D", "直接生成", "按当前信息执行"));
        OptionCardVO card = new OptionCardVO(options, OPTION_CARD_DEADLINE_SECONDS);
        state.setOptionCard(card);
        state.setReplyAction(ChatFlowState.REPLY_OPTION_CARD);
        state.setQuestion("出稿前最后一步：要不要加点增益？不选的话 30 秒后自动按当前信息生成～");
    }

    /** 挂起点2（选项卡）：写恢复载荷（含超时兜底扫描所需身份），只有这一次，不追问不循环。 */
    private void holdOptionCard(ChatFlowState state) {
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

    /** 选项卡恢复：应用 A/B/C/D 增益槽位后直进 ⑥（超时兜底不经过本节点，默认 D）。 */
    private void applyOption(ChatFlowState state) {
        loadContext(state);
        String key = state.getOptionKey();
        if ("A".equals(key)) {
            String festival = marketingCalendarService.nearest();
            if (festival != null) {
                state.setContextJson(mergeContext(state.getContextJson(), patch("festival", festival)));
            }
        } else if ("B".equals(key)) {
            String current = textOf(parse(state.getContextJson()), "tone");
            state.setContextJson(mergeContext(state.getContextJson(),
                    patch("tone", "正式".equals(current) ? "口语" : "正式")));
        } else if ("C".equals(key)) {
            state.setContextJson(mergeContext(state.getContextJson(), patch("versions", "4")));
        }
        // D 直接生成：无增益
        memoryService.saveSlots(state.getSession().getId(), state.getContextJson());
        memoryService.clearPending(state.getSession().getId());
        memoryService.track(state.tenantId(), "option_card_" + (key == null ? "D" : key));
    }

    // ==================== ⑥ generate_content 生成内容（模型适配器） ====================

    /**
     * 文案同步一次多版；配图/视频走 AI 网关异步任务（挂起点3：会话可关，前端轮询作品，
     * 生成失败额度自动退回）。Prompt 拼装含事实清单（to_verify 强制占位符）。
     */
    private void generateContent(ChatFlowState state) {
        ensureContext(state);
        if (ChatFlowState.TASK_COPY.equals(state.getTaskType())) {
            ensureFactReport(state);
            List<String> versions = null;
            try {
                String genContext = mergeContext(state.getContextJson(),
                        patch("facts", state.getFactReport().toPromptFragment()));
                LlmResponse response = llmProvider.complete(LlmRequest.chat(state.getSession().getScene(),
                        genContext, state.getHistoryJson(), state.getUserInput()));
                versions = response.getVersions();
            } catch (RuntimeException exception) {
                versions = null;
            }
            if (versions == null || versions.isEmpty() || allBlank(versions)) {
                versions = fallbackVersions(state);
            }
            state.setVersions(versions);
            state.setReplyAction(ChatFlowState.REPLY_GENERATE);
        } else {
            mediaSubmit(state);
        }
    }

    /**
     * 配图/视频：提交异步任务（AI 网关内部扣费+结算+失败退款），提交后挂起：
     * 快照槽位已落 gen_task（网关内），回调出片后作品入库，前端轮询作品状态。
     */
    private void mediaSubmit(ChatFlowState state) {
        JsonNode context = parse(state.getContextJson());
        GenerateRequest request = new GenerateRequest();
        request.setType(ChatFlowState.TASK_VIDEO.equals(state.getTaskType()) ? "video" : "image");
        request.setPlatform(defaultIfBlank(textOf(context, "platform"),
                defaultIfBlank(state.getSession().getScene(), "朋友圈")));
        request.setProductName(defaultIfBlank(textOf(context, "product"), "未指定商品"));
        StringBuilder input = new StringBuilder(defaultIfBlank(state.getSession().getScene(), ""));
        if (!isBlank(state.getUserInput())) {
            if (input.length() > 0) {
                input.append(" ");
            }
            input.append(state.getUserInput());
        }
        request.setUserInput(input.toString());
        request.setChatSessionId(state.getSession().getId());
        Work work = aiGatewayService.generate(request);
        state.setMediaWorkId(work.getId());
        state.setReplyAction(ChatFlowState.REPLY_PENDING_MEDIA);
        state.setQuestion((ChatFlowState.TASK_VIDEO.equals(state.getTaskType()) ? "视频" : "配图")
                + "任务已提交，正在生成中～完成后可在作品库查看，失败会自动退回额度。");
    }

    // ==================== ⑦ review_and_respond 审查+回复（工具+LLM） ====================

    /**
     * 合规检测：行业词库红线过滤；不过 → 带意见返回 ⑥ 重生成 1 次；
     * 仍不过 → 输出警示草稿（预扣额度不退，员工需人工检查）。
     */
    private void reviewAndRespond(ChatFlowState state) {
        List<String> filtered = filterVersions(state);
        if (allBlank(filtered) && !state.isRegenerated()) {
            state.setRegenerated(true);
            String genContext = mergeContext(state.getContextJson(),
                    patch("complianceHint", "严格避免「最高级、投资价值、保值升值」等珠宝行业红线词，改用生活化表达"));
            List<String> regenerated = null;
            try {
                LlmResponse response = llmProvider.complete(LlmRequest.chat(state.getSession().getScene(),
                        genContext, state.getHistoryJson(), state.getUserInput()));
                regenerated = response.getVersions();
            } catch (RuntimeException exception) {
                regenerated = null;
            }
            if (regenerated == null || regenerated.isEmpty() || allBlank(regenerated)) {
                regenerated = fallbackVersions(state);
            }
            state.setVersions(regenerated);
            filtered = filterVersions(state);
        }
        if (allBlank(filtered)) {
            List<String> warning = new ArrayList<String>();
            warning.add("【警示草稿】该内容触发行业合规红线，请人工检查调整后再发布。");
            state.setVersions(warning);
        } else {
            state.setVersions(filtered);
        }
        state.setReplyAction(ChatFlowState.REPLY_GENERATE);
    }

    /** 逐版合规过滤（level1 替换、level2 拦截返回空）。 */
    private List<String> filterVersions(ChatFlowState state) {
        List<String> filtered = new ArrayList<String>();
        for (String version : state.getVersions()) {
            filtered.add(complianceService.filterText(state.tenantId(), version));
        }
        return filtered;
    }

    /** 预扣确认（confirm）：预扣即扣费（同一事务，异常自动回滚=释放），此处为语义确认。 */
    private void billingConfirm(ChatFlowState state) {
        if (state.getHoldKey() != null) {
            billingService.confirmHold();
        }
    }

    /** 咨询分支：不进创作链路不扣费，直接口语化回复。 */
    private void respondConsult(ChatFlowState state) {
        String text = state.getUserInput();
        try {
            LlmResponse response = llmProvider.complete(LlmRequest.compose(
                    state.getSession().getScene(), state.getContextJson(), state.getHistoryJson(), text));
            state.setQuestion(response.getQuestion());
        } catch (RuntimeException exception) {
            state.setQuestion(null);
        }
        state.setReplyAction(ChatFlowState.REPLY_ASK);
    }

    // ==================== 微调链路（/revise 接口 + 对话内「再改改」意图共用） ====================

    /** 校验可微调：最近一次 GENERATE 消息存在、版本号在范围内，并计算本次微调序号。 */
    private void reviseValidate(ChatFlowState state) {
        ChatSession session = state.getSession();
        ChatMessage lastGenerate = messageMapper.findLatestGenerate(session.getId());
        if (lastGenerate == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "还没有可微调的文案版本");
        }
        List<String> baseVersions = versionsOf(lastGenerate.getContent());
        int versionNo;
        String instruction;
        if (state.getRevise() != null) {
            versionNo = state.getRevise().getVersionNo();
            instruction = state.getRevise().getInstruction();
        } else {
            // 对话内微调意图：默认改最新一版，修改指令即本轮输入
            versionNo = baseVersions.size();
            instruction = state.getUserInput();
        }
        if (versionNo < 1 || versionNo > baseVersions.size()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "版本号超出范围");
        }
        state.setBaseText(baseVersions.get(versionNo - 1));
        state.setRevisedFrom(versionNo);
        state.setReviseInstruction(instruction);
        state.setReviseSeq((session.getReviseCount() == null ? 0 : session.getReviseCount()) + 1);
        if (state.getContextJson() == null || state.getContextJson().trim().isEmpty()) {
            state.setContextJson(defaultContext(session));
            state.setHistoryJson("");
        }
    }

    /** 调 LLM 按指令改写指定版本，失败/空结果直接报错（不降级）。 */
    private void reviseCallLlm(ChatFlowState state) {
        LlmResponse response;
        try {
            response = llmProvider.complete(LlmRequest.revise(state.getSession().getScene(),
                    state.getContextJson(), state.getBaseText(), state.getReviseInstruction()));
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "改写失败，请稍后重试");
        }
        List<String> revised = response.getVersions();
        if (revised == null || revised.isEmpty() || revised.get(0) == null || revised.get(0).trim().isEmpty()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "改写失败，请稍后重试");
        }
        state.setVersions(revised);
        state.setReplyAction(ChatFlowState.REPLY_GENERATE);
    }

    /** 微调结果合规过滤：level2 拦截抛 4001；过滤后为空视为改写失败。 */
    private void reviseCompliance(ChatFlowState state) {
        String newText = complianceService.filterText(state.tenantId(), state.getVersions().get(0));
        if (newText == null || newText.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "改写失败，请稍后重试");
        }
        List<String> newVersions = new ArrayList<String>();
        newVersions.add(newText);
        state.setVersions(newVersions);
    }

    /** 微调计费（幂等键 chat:{sessionId}:rev:{n}，n 递增防重）。 */
    private void reviseBilling(ChatFlowState state) {
        billingService.chargeForRevise(state.getPrincipal(), state.getSession().getId(), state.getReviseSeq());
    }

    // ==================== 汇合：三段式回复 + 持久化 ====================

    /** 组装三段式回复（✅已确认事实 / 🎨创意表达 / ⚠️待核实）并生成 AI 消息。 */
    private void responseComposer(ChatFlowState state) {
        if (ChatFlowState.REPLY_GENERATE.equals(state.getReplyAction())) {
            state.setQuestion(composeThreeStage(state));
        }
        if (isBlank(state.getQuestion())) {
            state.setQuestion(FALLBACK_QUESTION);
        }
        state.setAiMessage(message(state, ChatMessage.ROLE_AI, aiJson(state)));
    }

    /** 三段式回复：事实/创意/待核实分层，出稿引导语 LLM 包装失败时模板兜底。 */
    private String composeThreeStage(ChatFlowState state) {
        StringBuilder question = new StringBuilder();
        FactReport report = state.getFactReport();
        if (report != null && (!report.getConfirmedFacts().isEmpty() || !report.getTodoVerifyFacts().isEmpty())) {
            question.append("✅ 已确认事实：").append(String.join("、", report.getConfirmedFacts())).append("\n");
            question.append("🎨 创意表达：以下内容由 AI 创作生成\n");
            question.append("⚠️ 待你核实：").append(report.getTodoVerifyFacts().isEmpty() ? "无"
                    : String.join("、", report.getTodoVerifyFacts())).append("\n\n");
        }
        try {
            LlmResponse response = llmProvider.complete(LlmRequest.compose(
                    state.getSession().getScene(), state.getContextJson(), state.getHistoryJson(),
                    state.getUserInput()));
            if (!isBlank(response.getQuestion())) {
                question.append(response.getQuestion().trim());
                return question.toString();
            }
        } catch (RuntimeException ignored) {
            // 组装失败用模板兜底
        }
        JsonNode context = parse(state.getContextJson());
        String product = textOf(context, "product");
        int count = state.getVersions() == null ? 0 : state.getVersions().size();
        question.append(isBlank(product) ? "" : "已按「" + product.trim() + "」")
                .append("出好 ").append(count).append(" 版内容，点任意一版可直接微调～");
        return question.toString();
    }

    /**
     * 持久化：AI 消息落库 + MySQL 会话槽位快照刷新 + Redis 会话记忆同步（断点续聊）。
     * 挂起状态（问卷/选项卡）已由各挂起节点写入 Redis，此处不再处理。
     */
    private void persist(ChatFlowState state) {
        messageMapper.insert(state.getAiMessage());
        sessionMapper.updateContext(state.tenantId(), state.getSession().getId(), state.getContextJson());
        memoryService.saveSlots(state.getSession().getId(), state.getContextJson());
        if (state.getMode() == ChatFlowState.Mode.REVISE
                || ChatFlowState.INTENT_REVISE.equals(state.getIntent())) {
            sessionMapper.incrReviseCount(state.tenantId(), state.getSession().getId());
            memoryService.track(state.tenantId(), "version_adopted");
        }
        state.setReply(reply(state.getSession(), state.getAiMessage(), state.getContextJson()));
    }

    // ==================== 辅助 ====================

    /** 恢复链路（OPTION/TIMEOUT）跳过 ①：generateContent 前确保上下文/历史就绪。 */
    private void ensureContext(ChatFlowState state) {
        if (state.getContextJson() == null || state.getContextJson().trim().isEmpty()) {
            ChatSession session = state.getSession();
            String slots = memoryService.loadSlots(session.getId());
            state.setContextJson(slots != null ? slots : defaultContext(session));
        }
        if (state.getHistoryJson() == null) {
            List<ChatMessage> recent = messageMapper.findRecent(state.getSession().getId(), HISTORY_MESSAGES);
            Collections.reverse(recent);
            state.setHistoryJson(historyJson(recent));
        }
    }

    /** 超时兜底恢复（TIMEOUT）跳过 ④：按当前槽位重算事实报告（幂等无副作用）。 */
    private void ensureFactReport(ChatFlowState state) {
        if (state.getFactReport().getConfirmedFacts().isEmpty()
                && state.getFactReport().getTodoVerifyFacts().isEmpty()) {
            verifyFacts(state);
        }
    }

    private ChatMessage message(ChatFlowState state, String role, String content) {
        ChatMessage message = new ChatMessage();
        message.setTenantId(state.tenantId());
        message.setSessionId(state.getSession().getId());
        message.setUserId(state.getSession().getUserId());
        message.setRole(role);
        message.setContent(content);
        return message;
    }

    /** AI 消息 JSON：动作 + 话术 + 版本 + 问卷/选项卡/媒体挂起载荷 + 微调来源。 */
    private String aiJson(ChatFlowState state) {
        try {
            Map<String, Object> payload = new LinkedHashMap<String, Object>();
            payload.put("action", state.getReplyAction());
            if (state.getQuestion() != null) {
                payload.put("question", state.getQuestion());
            }
            if (state.getVersions() != null) {
                payload.put("versions", state.getVersions());
            }
            if (state.getPendingQuestions() != null) {
                payload.put("questionnaire", state.getPendingQuestions());
            }
            if (state.getOptionCard() != null) {
                payload.put("optionCard", state.getOptionCard());
            }
            if (state.getMediaWorkId() != null) {
                payload.put("workId", state.getMediaWorkId());
            }
            if (state.getRevisedFrom() != null) {
                payload.put("revisedFrom", state.getRevisedFrom());
            }
            if (!state.getAiDecidedSlots().isEmpty()) {
                payload.put("aiDecidedSlots", state.getAiDecidedSlots());
            }
            return objectMapper.writeValueAsString(payload);
        } catch (Exception exception) {
            throw new IllegalStateException("无法序列化 AI 消息", exception);
        }
    }

    private ChatReplyVO reply(ChatSession session, ChatMessage aiMessage, String context) {
        ChatReplyVO vo = new ChatReplyVO();
        vo.setSessionId(session.getId());
        vo.setContext(context);
        vo.setMessageId(aiMessage.getId());
        try {
            JsonNode node = objectMapper.readTree(aiMessage.getContent());
            String action = node.path("action").asText("");
            vo.setAction(action);
            if (ChatFlowState.REPLY_GENERATE.equals(action)) {
                vo.setVersions(stringList(node.path("versions")));
            } else {
                vo.setQuestion(node.path("question").asText(FALLBACK_QUESTION));
            }
            if (ChatFlowState.REPLY_QUESTIONNAIRE.equals(action)) {
                vo.setQuestionnaire(questionsOf(node.path("questionnaire")));
            }
            if (ChatFlowState.REPLY_OPTION_CARD.equals(action)) {
                vo.setOptionCard(objectMapper.treeToValue(node.path("optionCard"), OptionCardVO.class));
            }
            if (node.hasNonNull("workId")) {
                vo.setWorkId(node.path("workId").asLong());
            }
        } catch (Exception exception) {
            vo.setAction(ChatFlowState.REPLY_ASK);
            vo.setQuestion(FALLBACK_QUESTION);
        }
        return vo;
    }

    private List<QuestionVO> questionsOf(JsonNode array) {
        List<QuestionVO> questions = new ArrayList<QuestionVO>();
        if (array != null && array.isArray()) {
            array.forEach(item -> {
                try {
                    questions.add(objectMapper.treeToValue(item, QuestionVO.class));
                } catch (Exception ignored) {
                    // 单题解析失败丢弃
                }
            });
        }
        return questions.isEmpty() ? null : questions;
    }

    private List<String> stringList(JsonNode array) {
        List<String> list = new ArrayList<String>();
        if (array != null && array.isArray()) {
            array.forEach(item -> list.add(item.asText("")));
        }
        return list.isEmpty() ? null : list;
    }

    private String historyJson(List<ChatMessage> recent) {
        try {
            List<Map<String, String>> history = new ArrayList<Map<String, String>>();
            for (ChatMessage item : recent) {
                Map<String, String> entry = new HashMap<String, String>();
                entry.put("role", item.getRole());
                entry.put("content", item.getContent());
                history.add(entry);
            }
            return objectMapper.writeValueAsString(history);
        } catch (Exception exception) {
            return "[]";
        }
    }

    /** 把 LLM 返回的槽位补丁合并回会话快照（浅合并，LLM 值覆盖旧值；null 值跳过不污染）。 */
    private String mergeContext(String contextJson, String patchJson) {
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

    private List<String> versionsOf(String aiContent) {
        try {
            JsonNode node = objectMapper.readTree(aiContent);
            List<String> versions = new ArrayList<String>();
            JsonNode array = node.path("versions");
            if (array.isArray()) {
                array.forEach(item -> versions.add(item.asText("")));
            }
            if (versions.isEmpty()) {
                throw new BusinessException(ErrorCode.INVALID_PARAMETER, "历史消息里没有文案版本");
            }
            return versions;
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "历史消息格式异常");
        }
    }

    /** 出稿失败的模板兜底（与 demo provider 风格一致）。 */
    private List<String> fallbackVersions(ChatFlowState state) {
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

    private String jsonList(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (Exception exception) {
            return "[]";
        }
    }

    private String patch(String key, String value) {
        try {
            return objectMapper.writeValueAsString(Collections.singletonMap(key, value));
        } catch (Exception exception) {
            return null;
        }
    }

    private JsonNode parse(String json) {
        try {
            return objectMapper.readTree(json == null || json.trim().isEmpty() ? "{}" : json);
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

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String defaultIfBlank(String value, String fallback) {
        return isBlank(value) ? fallback : value.trim();
    }

    private boolean allBlank(List<String> values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private @NonNull Map<String, String> branches(@NonNull String... keyValues) {
        Map<String, String> mapping = new HashMap<String, String>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            mapping.put(keyValues[i], keyValues[i + 1]);
        }
        return mapping;
    }
}
