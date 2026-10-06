package com.xiaoa.ai.chat.agent;

import com.xiaoa.ai.chat.graph.ChatFlowState;
import com.xiaoa.ai.chat.graph.FactReport;
import com.xiaoa.ai.chat.mapper.ChatMessageMapper;
import com.xiaoa.ai.chat.model.ChatMessage;
import com.xiaoa.ai.chat.provider.AgentPromptProperties;
import com.xiaoa.ai.chat.provider.LlmProvider;
import com.xiaoa.ai.chat.provider.LlmRequest;
import com.xiaoa.ai.chat.provider.LlmResponse;
import com.xiaoa.ai.chat.service.ChatBillingService;
import com.xiaoa.ai.chat.service.ChatMemoryService;
import com.xiaoa.admin.service.ComplianceService;
import com.xiaoa.common.exception.BusinessException;
import com.xiaoa.common.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * ② 内容生成 Agent（generateContent 节点）：
 * 信息足够后调用大模型出多版文案；同时吸收三件配套事（保持额度与合规语义不变）：
 * <ul>
 *   <li>额度：文案创作生成前预扣（hold，同一事务内，失败回滚即自动释放）；额度不足不生成，
 *       置 quotaOk=false 交整合节点出充值提示；</li>
 *   <li>微调：REST /chat/revise 或对话 REVISE 意图 → 按「最近一次出稿 + 指令」改写（成功后计费，幂等键防重）；</li>
 *   <li>合规：逐版行业红线过滤（level1 替换、level2 拦截），全拦输出警示草稿（预扣不退）。</li>
 * </ul>
 * LLM 失败/空结果用模板兜底，保证链路可用。
 *
 * <p>隔离记忆：本轮生成版数与是否微调。</p>
 */
@Component
public class GenerateAgent extends BaseNodeAgent {

    private final LlmProvider llmProvider;
    private final ContextLoader contextLoader;
    private final ChatBillingService billingService;
    private final ChatMemoryService memoryService;
    private final ChatMessageMapper messageMapper;
    private final ComplianceService complianceService;

    public GenerateAgent(AgentMemoryService agentMemory, AgentPromptProperties prompts, LlmProvider llmProvider,
                         ContextLoader contextLoader, ChatBillingService billingService, ChatMemoryService memoryService,
                         ChatMessageMapper messageMapper, ComplianceService complianceService) {
        super(agentMemory, prompts);
        this.llmProvider = llmProvider;
        this.contextLoader = contextLoader;
        this.billingService = billingService;
        this.memoryService = memoryService;
        this.messageMapper = messageMapper;
        this.complianceService = complianceService;
    }

    @Override
    public String name() {
        return "generateAgent";
    }

    @Override
    public String systemPrompt() {
        // GenerateAgent 主提示词 = 新创作出稿（YAML xiaoa.ai.llm.prompts.chat 可配）；
        // 微调改写用 prompts.getRevise()，随请求显式携带
        return prompts.getChat();
    }

    @Override
    public void invoke(ChatFlowState state) {
        contextLoader.load(state);
        boolean revise = state.isReviseMode() || ChatFlowState.INTENT_REVISE.equals(state.getIntent());
        if (revise) {
            revise(state);
        } else if (!checkAndHoldQuota(state)) {
            // 额度不足：不生成、不计费，直接出充值提示
            state.setReplyAction(ChatFlowState.REPLY_ASK);
            state.setQuestion("当前额度不足（本次创作约需 " + state.getQuotaNeed()
                    + " 点），请联系店长或管理员充值后再试～");
            remember(state, "quotaNeed", String.valueOf(state.getQuotaNeed()));
            return;
        } else {
            create(state);
        }
        filterCompliance(state);
        state.setReplyAction(ChatFlowState.REPLY_GENERATE);
        remember(state, "versionsCount", String.valueOf(state.getVersions() == null ? 0 : state.getVersions().size()));
        remember(state, "revise", String.valueOf(revise));
    }

    // ==================== 新创作 ====================

    /** 新创作：事实核验 + 技能资料并入生成上下文 → LLM 出多版。 */
    private void create(ChatFlowState state) {
        state.setFactReport(new FactReport());
        verifyFacts(state);
        List<String> versions = null;
        try {
            String genContext = mergeContext(state.getContextJson(),
                    patch("facts", state.getFactReport().toPromptFragment()));
            if (state.getSkillFacts() != null) {
                genContext = mergeContext(genContext, patch("skillFacts", state.getSkillFacts()));
            }
            LlmResponse response = llmProvider.complete(withPrompt(LlmRequest.chat(state.getSession().getScene(),
                    genContext, "[]", state.getUserInput())));
            versions = response.getVersions();
        } catch (RuntimeException exception) {
            versions = null;
        }
        if (versions == null || versions.isEmpty() || allBlank(versions)) {
            versions = fallbackVersions(state);
        }
        state.setVersions(versions);
    }

    // ==================== 微调（REST /chat/revise 与对话 REVISE 意图共用） ====================

    /** 定位最近出稿 → LLM 改写 → 计费（幂等键防重，失败事务回滚不计费）。 */
    private void revise(ChatFlowState state) {
        ChatMessage lastGenerate = messageMapper.findLatestGenerate(state.getSession().getId());
        if (lastGenerate == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "还没有可微调的文案版本");
        }
        List<String> baseVersions = versionsOf(lastGenerate.getContent());
        int versionNo;
        String instruction;
        if (state.isReviseMode() && state.getRevise() != null) {
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
        state.setReviseSeq((state.getSession().getReviseCount() == null ? 0 : state.getSession().getReviseCount()) + 1);

        List<String> revised;
        try {
            LlmResponse response = llmProvider.complete(withPrompt(LlmRequest.revise(state.getSession().getScene(),
                    state.getContextJson(), state.getBaseText(), state.getReviseInstruction()),
                    prompts.getRevise()));
            revised = response.getVersions();
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "改写失败，请稍后重试");
        }
        if (revised == null || revised.isEmpty() || revised.get(0) == null || revised.get(0).trim().isEmpty()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "改写失败，请稍后重试");
        }
        state.setVersions(revised);
        // 计费（幂等键 chat:{sessionId}:rev:{n}）；incrReviseCount 由整合节点落库时推进
        billingService.chargeForRevise(state.getPrincipal(), state.getSession().getId(), state.getReviseSeq());
        remember(state, "revisedFrom", String.valueOf(versionNo));
        remember(state, "instruction", String.valueOf(instruction));
    }

    // ==================== 额度与合规 ====================

    /** 文案创作额度预扣（hold）：预扣即扣费，异常整体回滚即自动释放；不足返回 false。 */
    private boolean checkAndHoldQuota(ChatFlowState state) {
        state.setQuotaNeed(billingService.getCopyCost());
        String holdKey = "chat:" + state.getSession().getId() + ":hold:"
                + memoryService.nextSeq(state.getSession().getId());
        try {
            billingService.checkAndHold(state.getPrincipal(), holdKey);
            state.setHoldKey(holdKey);
            state.setQuotaOk(true);
            return true;
        } catch (BusinessException exception) {
            if (exception.getCode() == ErrorCode.QUOTA_NOT_ENOUGH.getCode()) {
                state.setQuotaOk(false);
                return false;
            }
            throw exception;
        }
    }

    /** 逐版合规过滤（level1 替换、level2 拦截返回空）；全拦输出警示草稿（预扣不退）。 */
    private void filterCompliance(ChatFlowState state) {
        List<String> filtered = new ArrayList<String>();
        for (String version : state.getVersions()) {
            filtered.add(complianceService.filterText(state.tenantId(), version));
        }
        if (allBlank(filtered)) {
            List<String> warning = new ArrayList<String>();
            warning.add("【警示草稿】该内容触发行业合规红线，请人工检查调整后再发布。");
            state.setVersions(warning);
        } else {
            state.setVersions(filtered);
        }
    }

    /** 事实核验（不臆造）：槽位内字段视为已确认资料，缺失字段列 to_verify 强制占位符。 */
    private void verifyFacts(ChatFlowState state) {
        FactReport report = state.getFactReport();
        com.fasterxml.jackson.databind.JsonNode context = parse(state.getContextJson());
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

    /** 解析最近 GENERATE 消息里的文案版本（微调取稿用）。 */
    private List<String> versionsOf(String aiContent) {
        try {
            com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(aiContent);
            List<String> versions = new ArrayList<String>();
            com.fasterxml.jackson.databind.JsonNode array = node.path("versions");
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
}
