package com.xiaoa.ai.chat.graph;

import com.xiaoa.ai.chat.dto.ChatReplyVO;
import com.xiaoa.ai.chat.dto.ChatReviseRequest;
import com.xiaoa.ai.chat.dto.OptionCardVO;
import com.xiaoa.ai.chat.dto.QuestionVO;
import com.xiaoa.ai.chat.model.ChatMessage;
import com.xiaoa.ai.chat.model.ChatSession;
import com.xiaoa.common.auth.AuthPrincipal;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 对话流状态（方案 §5 ChatState 定义的运行时载体，LangGraph State 语义）：
 * 槽位/缺口/AI 代选/问卷轮次/挂起载荷/事实核验报告/生成结果 全程随图传递。
 *
 * <p>恢复类型（挂起点的 resume 语义）：</p>
 * <ul>
 *   <li>RESUME_NEW：正常新消息，从 Gate（意图+充分性）开始</li>
 *   <li>RESUME_OPTION：选项卡选择恢复 → 应用用户选择 → 回 Gate 复判（循环）</li>
 *   <li>RESUME_TIMEOUT：选项卡超时兜底 → AI 代选补齐 → 技能取数 → 直接生成</li>
 * </ul>
 */
public class ChatFlowState {

    /** 正常新消息入口 */
    public static final String RESUME_NEW = "NEW";
    /** 选项卡选择恢复（挂起点） */
    public static final String RESUME_OPTION = "OPTION";
    /** 选项卡超时兜底恢复（默认 D 直接生成） */
    public static final String RESUME_TIMEOUT = "TIMEOUT";

    /** 意图：新创作 / 微调 / 咨询（方案 ② 三分类） */
    public static final String INTENT_NEW_CREATE = "NEW_CREATE";
    public static final String INTENT_REVISE = "REVISE";
    public static final String INTENT_CONSULT = "CONSULT";

    /** 任务类型：文案 / 配图 / 视频（视频走 interrupt 挂起，异步回调恢复） */
    public static final String TASK_COPY = "COPY";
    public static final String TASK_IMAGE = "IMAGE";
    public static final String TASK_VIDEO = "VIDEO";

    /** Gate 判定：信息足够 → 技能取数 + LLM 直接生成 */
    public static final String SUFFICIENT_ENOUGH = "ENOUGH";
    /** Gate 判定：信息不足 → 出选项卡挂起，用户选择后再判 */
    public static final String SUFFICIENT_NOT = "NOT_ENOUGH";

    /** 回复推进动作（前后端契约） */
    public static final String REPLY_ASK = "ASK";
    public static final String REPLY_GENERATE = "GENERATE";
    public static final String REPLY_QUESTIONNAIRE = "QUESTIONNAIRE";
    public static final String REPLY_OPTION_CARD = "OPTION_CARD";
    public static final String REPLY_PENDING_MEDIA = "PENDING_MEDIA";

    public enum Mode { CHAT, REVISE }

    // ==================== 基础上下文 ====================
    private Mode mode;
/** 图线程标识（LangGraph thread_id 语义）：<b>任务 id，独立于会话 ID</b>，每次编排生成一次；
 * 短期记忆（Agent 隔离记忆 / checkpoint）按它定位，任务走到最后节点即释放。 */
private String threadId;
    private AuthPrincipal principal;
    private ChatSession session;
    private ChatReviseRequest revise;
    /** 用户本轮输入 */
    private String userInput;
    /** 恢复类型：NEW/ANSWER/OPTION/TIMEOUT */
    private String resumeType;

    // ==================== ① fetch_context 产出 ====================
    /** 合并后的槽位快照 JSON（Redis 记忆优先，miss 回退 MySQL 会话上下文） */
    private String contextJson;
    /** 最近历史 [{"role","content"}] */
    private String historyJson;
    /** 未来 30 天营销节点（如「520(5-20)」） */
    private List<String> calendarFestivals = new ArrayList<>();

    // ==================== ② understand_intent 产出 ====================
    private String intent;
    private String taskType;
    /** 必填缺口（代码按槽位字典计算，如 ["product"]） */
    private List<String> missingRequired = new ArrayList<>();
    /** 增益候选（festival/tone/versions 中未定的） */
    private List<String> enhancementCandidates = new ArrayList<>();

    // ==================== ②' gate_assess 产出（充分性判断 Agent） ====================
    /** Gate 判定：ENOUGH / NOT_ENOUGH */
    private String sufficiency;
    /** Gate 判定的信息缺口（自然语言，如「商品方向」） */
    private List<String> gateMissing = new ArrayList<>();
    /** Gate 给出的候选方向（选项卡 A/B/C 文案来源） */
    private List<String> gateOptions = new ArrayList<>();
    /** Gate 判定生成前需要的技能（经注册表校验） */
    private List<String> needSkills = new ArrayList<>();
    /** Gate 判定理由（观测/埋点） */
    private String gateReason;
    /** Gate 已进行的判断轮次（含选项卡循环） */
    private int gateRound;
    /** 用户已选「直接生成」：Gate 不再询问，AI 代选补齐后直接生成 */
    private boolean directGenerate;
    /** SkillAgent 取回的资料 JSON（[{skill,info}]） */
    private String skillFacts;

    // ==================== ③/⑤ 挂起载荷 ====================
    /** 问卷轮次（已消耗轮数，≤2） */
    private int questionnaireRound;
    /** 当前问卷（挂起点1 载荷） */
    private List<QuestionVO> pendingQuestions;
    /** 问卷作答（恢复时携带） */
    private Map<String, String> answers = new ConcurrentHashMap<>();
    /** 当前选项卡（挂起点2 载荷） */
    private OptionCardVO optionCard;
    /** 选项卡选择（恢复时携带 A/B/C/D；超时恢复为 null） */
    private String optionKey;
    /** AI 代选的槽位（成品需标注「AI 代选，可改」） */
    private List<String> aiDecidedSlots = new ArrayList<>();

    // ==================== ④ verify_and_charge 产出 ====================
    private FactReport factReport = new FactReport();
    /** 额度预扣幂等键 chat:{sessionId}:hold:{seq}（confirm/release 用） */
    private String holdKey;
    private long quotaNeed;
    private boolean quotaOk;

    // ==================== ⑥/⑦ 产出 ====================
    private List<String> versions;
    /** 合规不过重生成标志（只重生 1 次） */
    private boolean regenerated;
    /** 回复推进动作 */
    private String replyAction;
    private String question;
    private String reviseInstruction;
    private String baseText;
    private Integer revisedFrom;
    private int reviseSeq;
    private Long mediaWorkId;

    private ChatReplyVO reply = new ChatReplyVO();
    private ChatMessage aiMessage;

    // ==================== 工厂 ====================

    /** 生成新任务 id：与会话 id 分离，同一会话多次创作各自独立（记忆互不残留）。 */
    private static String newTaskId(Long sessionId) {
        return "task-" + sessionId + "-" + java.util.UUID.randomUUID().toString().substring(0, 8);
    }

    /** 正常新消息：从 ① fetch_context 开始（生成新任务 id） */
    public static ChatFlowState forChat(AuthPrincipal principal, ChatSession session, String userInput) {
        ChatFlowState state = new ChatFlowState();
        state.mode = Mode.CHAT;
        state.threadId = newTaskId(session.getId());
        state.principal = principal;
        state.session = session;
        state.userInput = userInput;
        state.resumeType = RESUME_NEW;
        return state;
    }

    /** 选项卡选择恢复：应用用户选择后回 Gate 复判 */
    public static ChatFlowState forOption(AuthPrincipal principal, ChatSession session, String optionKey) {
        ChatFlowState state = forChat(principal, session, null);
        state.resumeType = RESUME_OPTION;
        state.optionKey = optionKey;
        return state;
    }

    /** 选项卡超时兜底：默认 D 直接生成 */
    public static ChatFlowState forTimeout(AuthPrincipal principal, ChatSession session) {
        ChatFlowState state = forChat(principal, session, null);
        state.resumeType = RESUME_TIMEOUT;
        return state;
    }

    /** 微调：从指定版本按指令改写（老链路保持；生成新任务 id） */
    public static ChatFlowState forRevise(AuthPrincipal principal, ChatSession session,
                                           ChatReviseRequest revise) {
        ChatFlowState state = new ChatFlowState();
        state.mode = Mode.REVISE;
        state.threadId = newTaskId(session.getId());
        state.principal = principal;
        state.session = session;
        state.revise = revise;
        state.resumeType = RESUME_NEW;
        return state;
    }

    public boolean isReviseMode() {
        return mode == Mode.REVISE;
    }

    /** 租户 ID（当前登录态）；超时兑底任务重建 principal 后同样可用。 */
    public Long tenantId() {
        return principal == null ? null : principal.getTenantId();
    }

    public ChatMessage getAiMessage() { return aiMessage; }
    public void setAiMessage(ChatMessage aiMessage) { this.aiMessage = aiMessage; }

    // ==================== getter / setter ====================

    public Mode getMode() { return mode; }
    public void setMode(Mode mode) { this.mode = mode; }
    public String getThreadId() { return threadId; }
    public void setThreadId(String threadId) { this.threadId = threadId; }
    public AuthPrincipal getPrincipal() { return principal; }
    public void setPrincipal(AuthPrincipal principal) { this.principal = principal; }
    public ChatSession getSession() { return session; }
    public void setSession(ChatSession session) { this.session = session; }
    public ChatReviseRequest getRevise() { return revise; }
    public String getUserInput() { return userInput; }
    public void setUserInput(String userInput) { this.userInput = userInput; }
    public String getResumeType() { return resumeType; }
    public void setResumeType(String resumeType) { this.resumeType = resumeType; }
    public String getContextJson() { return contextJson; }
    public void setContextJson(String contextJson) { this.contextJson = contextJson; }
    public String getHistoryJson() { return historyJson; }
    public void setHistoryJson(String historyJson) { this.historyJson = historyJson; }
    public List<String> getCalendarFestivals() { return calendarFestivals; }
    public void setCalendarFestivals(List<String> calendarFestivals) { this.calendarFestivals = calendarFestivals; }
    public String getIntent() { return intent; }
    public void setIntent(String intent) { this.intent = intent; }
    public String getTaskType() { return taskType; }
    public void setTaskType(String taskType) { this.taskType = taskType; }
    public List<String> getMissingRequired() { return missingRequired; }
    public void setMissingRequired(List<String> missingRequired) { this.missingRequired = missingRequired; }
    public List<String> getEnhancementCandidates() { return enhancementCandidates; }
    public void setEnhancementCandidates(List<String> enhancementCandidates) { this.enhancementCandidates = enhancementCandidates; }
    public int getQuestionnaireRound() { return questionnaireRound; }
    public void setQuestionnaireRound(int questionnaireRound) { this.questionnaireRound = questionnaireRound; }
    public List<QuestionVO> getPendingQuestions() { return pendingQuestions; }
    public void setPendingQuestions(List<QuestionVO> pendingQuestions) { this.pendingQuestions = pendingQuestions; }
    public Map<String, String> getAnswers() { return answers; }
    public OptionCardVO getOptionCard() { return optionCard; }
    public void setOptionCard(OptionCardVO optionCard) { this.optionCard = optionCard; }
    public String getOptionKey() { return optionKey; }
    public void setOptionKey(String optionKey) { this.optionKey = optionKey; }
    public List<String> getAiDecidedSlots() { return aiDecidedSlots; }
    public FactReport getFactReport() { return factReport; }
    public void setFactReport(FactReport factReport) { this.factReport = factReport; }
    public String getHoldKey() { return holdKey; }
    public void setHoldKey(String holdKey) { this.holdKey = holdKey; }
    public long getQuotaNeed() { return quotaNeed; }
    public void setQuotaNeed(long quotaNeed) { this.quotaNeed = quotaNeed; }
    public boolean isQuotaOk() { return quotaOk; }
    public void setQuotaOk(boolean quotaOk) { this.quotaOk = quotaOk; }
    public List<String> getVersions() { return versions; }
    public void setVersions(List<String> versions) { this.versions = versions; }
    public boolean isRegenerated() { return regenerated; }
    public void setRegenerated(boolean regenerated) { this.regenerated = regenerated; }
    public String getReplyAction() { return replyAction; }
    public void setReplyAction(String replyAction) { this.replyAction = replyAction; }
    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
    public String getReviseInstruction() { return reviseInstruction; }
    public void setReviseInstruction(String reviseInstruction) { this.reviseInstruction = reviseInstruction; }
    public String getBaseText() { return baseText; }
    public void setBaseText(String baseText) { this.baseText = baseText; }
    public Integer getRevisedFrom() { return revisedFrom; }
    public void setRevisedFrom(Integer revisedFrom) { this.revisedFrom = revisedFrom; }
    public int getReviseSeq() { return reviseSeq; }
    public void setReviseSeq(int reviseSeq) { this.reviseSeq = reviseSeq; }
    public Long getMediaWorkId() { return mediaWorkId; }
    public void setMediaWorkId(Long mediaWorkId) { this.mediaWorkId = mediaWorkId; }
    public ChatReplyVO getReply() { return reply; }
    public void setReply(ChatReplyVO reply) { this.reply = reply; }

    // ==================== Gate / Skill（Agent 化新增） ====================

    public String getSufficiency() { return sufficiency; }
    public void setSufficiency(String sufficiency) { this.sufficiency = sufficiency; }
    public boolean isSufficient() { return SUFFICIENT_ENOUGH.equals(sufficiency); }
    public List<String> getGateMissing() { return gateMissing; }
    public void setGateMissing(List<String> gateMissing) { this.gateMissing = gateMissing; }
    public List<String> getGateOptions() { return gateOptions; }
    public void setGateOptions(List<String> gateOptions) { this.gateOptions = gateOptions; }
    public List<String> getNeedSkills() { return needSkills; }
    public void setNeedSkills(List<String> needSkills) { this.needSkills = needSkills; }
    public String getGateReason() { return gateReason; }
    public void setGateReason(String gateReason) { this.gateReason = gateReason; }
    public int getGateRound() { return gateRound; }
    public void setGateRound(int gateRound) { this.gateRound = gateRound; }
    public boolean isDirectGenerate() { return directGenerate; }
    public void setDirectGenerate(boolean directGenerate) { this.directGenerate = directGenerate; }
    public String getSkillFacts() { return skillFacts; }
    public void setSkillFacts(String skillFacts) { this.skillFacts = skillFacts; }
}
