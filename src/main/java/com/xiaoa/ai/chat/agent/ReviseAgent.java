package com.xiaoa.ai.chat.agent;

import com.xiaoa.ai.chat.graph.ChatFlowState;
import com.xiaoa.ai.chat.mapper.ChatMessageMapper;
import com.xiaoa.ai.chat.model.ChatMessage;
import com.xiaoa.ai.chat.provider.LlmProvider;
import com.xiaoa.ai.chat.provider.LlmRequest;
import com.xiaoa.ai.chat.provider.LlmResponse;
import com.xiaoa.admin.service.ComplianceService;
import com.xiaoa.ai.chat.service.ChatBillingService;
import com.xiaoa.common.exception.BusinessException;
import com.xiaoa.common.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 微调 Agent（reviseValidate / reviseCallLlm / reviseCompliance / reviseBilling 四个节点）：
 * 校验可微调 → 调 LLM 按指令改写指定版本 → 合规过滤 → 计费（幂等键防重）。
 * 失败/空结果直接报错（不降级），事务回滚即不计费。
 *
 * <p>隔离记忆：微调来源版本号与指令。</p>
 */
@Component
public class ReviseAgent extends BaseNodeAgent {

    private final LlmProvider llmProvider;
    private final ComplianceService complianceService;
    private final ChatBillingService billingService;
    private final ChatMessageMapper messageMapper;
    private final ContextLoader contextLoader;

    public ReviseAgent(AgentMemoryService agentMemory, LlmProvider llmProvider,
                       ComplianceService complianceService, ChatBillingService billingService,
                       ChatMessageMapper messageMapper, ContextLoader contextLoader) {
        super(agentMemory);
        this.llmProvider = llmProvider;
        this.complianceService = complianceService;
        this.billingService = billingService;
        this.messageMapper = messageMapper;
        this.contextLoader = contextLoader;
    }

    @Override
    public String name() {
        return "reviseAgent";
    }

    @Override
    public String systemPrompt() {
        return "微调 Agent：校验可微调版本 → LLM 按指令改写 → 合规过滤（level2 拦截视为失败）→ 计费（幂等键防重）；失败不降级直接报错回滚。";
    }

    /** 默认入口 = 校验（图上按 validate/callLlm/compliance/billing 四个节点引用）。 */
    @Override
    public void invoke(ChatFlowState state) {
        validate(state);
    }

    // ==================== reviseValidate 节点 ====================

    /** 校验可微调：最近一次 GENERATE 消息存在、版本号在范围内，并计算本次微调序号。 */
    public void validate(ChatFlowState state) {
        ChatMessage lastGenerate = messageMapper.findLatestGenerate(state.getSession().getId());
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
        state.setReviseSeq((state.getSession().getReviseCount() == null ? 0 : state.getSession().getReviseCount()) + 1);
        if (state.getContextJson() == null || state.getContextJson().trim().isEmpty()) {
            state.setContextJson(contextLoader.defaultContext(state.getSession()));
            state.setHistoryJson("[]");
        }
        // 隔离记忆：微调来源
        remember(state, "revisedFrom", String.valueOf(versionNo));
        remember(state, "instruction", String.valueOf(instruction));
    }

    // ==================== reviseCallLlm 节点 ====================

    /** 调 LLM 按指令改写指定版本，失败/空结果直接报错（不降级）。 */
    public void callLlm(ChatFlowState state) {
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

    // ==================== reviseCompliance 节点 ====================

    /** 微调结果合规过滤：level2 拦截抛 4001；过滤后为空视为改写失败。 */
    public void compliance(ChatFlowState state) {
        String newText = complianceService.filterText(state.tenantId(), state.getVersions().get(0));
        if (newText == null || newText.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "改写失败，请稍后重试");
        }
        List<String> newVersions = new ArrayList<String>();
        newVersions.add(newText);
        state.setVersions(newVersions);
    }

    // ==================== reviseBilling 节点 ====================

    /** 微调计费（幂等键 chat:{sessionId}:rev:{n}，n 递增防重）。 */
    public void billing(ChatFlowState state) {
        billingService.chargeForRevise(state.getPrincipal(), state.getSession().getId(), state.getReviseSeq());
    }

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
