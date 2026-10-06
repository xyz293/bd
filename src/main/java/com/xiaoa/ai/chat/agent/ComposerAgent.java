package com.xiaoa.ai.chat.agent;

import com.xiaoa.ai.chat.dto.OptionCardVO;
import com.xiaoa.ai.chat.graph.ChatFlowState;
import com.xiaoa.ai.chat.mapper.ChatMessageMapper;
import com.xiaoa.ai.chat.mapper.ChatSessionMapper;
import com.xiaoa.ai.chat.model.ChatMessage;
import com.xiaoa.ai.chat.model.ChatSession;
import com.xiaoa.ai.chat.provider.AgentPromptProperties;
import com.xiaoa.ai.chat.provider.LlmProvider;
import com.xiaoa.ai.chat.provider.LlmRequest;
import com.xiaoa.ai.chat.provider.LlmResponse;
import com.xiaoa.ai.chat.service.ChatCheckpointService;
import com.xiaoa.ai.chat.service.ChatMemoryService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ③ 整合 Agent（compose 节点，全图唯一出口）：
 * 各分支在统一出口收口——
 * <ul>
 *   <li>咨询（CONSULT）：口语化回复，不进创作链路、不扣额度；</li>
 *   <li>额度不足：生成节点已出充值提示，此处照常落库；</li>
 *   <li>生成出稿：三段式包装（✅已确认事实 / 🎨创意表达 / ⚠️待核实）+ 出稿引导语；</li>
 *   <li>选项卡挂起：沿用 OptionAgent 已设的题干与选项卡。</li>
 * </ul>
 * 最终组装 AI 消息 JSON 并落库、组装回复 VO，微调请求推进微调计数；
 * <b>选项卡挂起时保存图状态 checkpoint 供恢复；作品生成/回复落库等终结态则释放当前任务
 * id 的全部任务态</b>（各 Agent 隔离记忆 + 挂起标记），下次创作从全新任务态开始。
 *
 * <p>隔离记忆：本轮最终回复动作（终结态释放，不跨任务残留）。</p>
 */
@Component
public class ComposerAgent extends BaseNodeAgent {

    private final LlmProvider llmProvider;
    private final ChatMessageMapper messageMapper;
    private final ChatSessionMapper sessionMapper;
    private final ChatCheckpointService checkpointService;
    private final ChatMemoryService memoryService;

    public ComposerAgent(AgentMemoryService agentMemory, AgentPromptProperties prompts, LlmProvider llmProvider,
                         ChatMessageMapper messageMapper, ChatSessionMapper sessionMapper,
                         ChatCheckpointService checkpointService, ChatMemoryService memoryService) {
        super(agentMemory, prompts);
        this.llmProvider = llmProvider;
        this.messageMapper = messageMapper;
        this.sessionMapper = sessionMapper;
        this.checkpointService = checkpointService;
        this.memoryService = memoryService;
    }

    @Override
    public String name() {
        return "composerAgent";
    }

    @Override
    public String systemPrompt() {
        return prompts.getCompose(); // ComposerAgent 专属提示词（YAML xiaoa.ai.llm.prompts.compose 可配）
    }

    @Override
    public void invoke(ChatFlowState state) {
        if (ChatFlowState.INTENT_CONSULT.equals(state.getIntent())) {
            consultReply(state);
        } else if (ChatFlowState.REPLY_GENERATE.equals(state.getReplyAction())) {
            state.setQuestion(composeThreeStage(state));
        }
        if (isBlank(state.getQuestion())) {
            state.setQuestion(FALLBACK_QUESTION);
        }
        persist(state);
        remember(state, "replyAction", String.valueOf(state.getReplyAction()));
        releaseTaskState(state);
    }

    // ==================== 任务态释放 ====================

    /**
     * 编排终结（非选项卡挂起）后释放当前任务 id 的全部任务态：
     * 各 Agent 隔离记忆 + 挂起标记 + 会话→任务 id 映射。任务 id 与会话 id 分离，
     * 作品已生成、回复已落库 = 任务完成，该任务 id 的短期记忆即从 Redis 删除；
     * 下一次创作（即使同会话）生成全新任务 id，不复用旧任务的中间产物
     * （如 Gate 判断轮次、技能调用轨迹）。
     */
    private void releaseTaskState(ChatFlowState state) {
        if (state.getOptionCard() != null) {
            return; // 选项卡挂起：隔离记忆/挂起标记/任务映射保留，供 /option 与超时兑底恢复
        }
        agentMemory.clearThread(state.getThreadId());
        memoryService.clearPending(state.getSession().getId());
        memoryService.releaseTask(state.getSession().getId());
    }

    // ==================== 咨询回复 ====================

    /** 咨询：口语化回复（LLM 失败用固定话术兜底），不扣费。 */
    private void consultReply(ChatFlowState state) {
        state.setReplyAction(ChatFlowState.REPLY_ASK);
        try {
            LlmResponse response = llmProvider.complete(withPrompt(LlmRequest.compose(
                    state.getSession().getScene(), state.getContextJson(), "[]", state.getUserInput())));
            state.setQuestion(isBlank(response.getQuestion()) ? null : response.getQuestion());
        } catch (RuntimeException exception) {
            state.setQuestion(null);
        }
        if (isBlank(state.getQuestion())) {
            state.setQuestion("我在呢～需要写朋友圈/小红书/抖音内容随时说，先告诉我发哪个平台、想达成什么～");
        }
    }

    /** 三段式回复：事实/创意/待核实分层，出稿引导语 LLM 包装失败时模板兜底。 */
    private String composeThreeStage(ChatFlowState state) {
        StringBuilder question = new StringBuilder();
        com.xiaoa.ai.chat.graph.FactReport report = state.getFactReport();
        if (report != null && (!report.getConfirmedFacts().isEmpty() || !report.getTodoVerifyFacts().isEmpty())) {
            question.append("✅ 已确认事实：").append(String.join("、", report.getConfirmedFacts())).append("\n");
            question.append("🎨 创意表达：以下内容由 AI 创作生成\n");
            question.append("⚠️ 待你核实：").append(report.getTodoVerifyFacts().isEmpty() ? "无"
                    : String.join("、", report.getTodoVerifyFacts())).append("\n\n");
        }
        try {
            LlmResponse response = llmProvider.complete(withPrompt(LlmRequest.compose(
                    state.getSession().getScene(), state.getContextJson(), "[]", state.getUserInput())));
            if (!isBlank(response.getQuestion())) {
                question.append(response.getQuestion().trim());
                return question.toString();
            }
        } catch (RuntimeException ignored) {
            // 组装失败用模板兜底
        }
        com.fasterxml.jackson.databind.JsonNode context = parse(state.getContextJson());
        String product = textOf(context, "product");
        int count = state.getVersions() == null ? 0 : state.getVersions().size();
        question.append(isBlank(product) ? "" : "已按「" + product.trim() + "」")
                .append("出好 ").append(count).append(" 版内容，点任意一版可直接微调～");
        return question.toString();
    }

    // ==================== 落库与收尾 ====================

    /** AI 消息落库 + checkpoint（仅选项卡挂起时保存，供恢复）+ 微调计数 + 回复 VO。 */
    private void persist(ChatFlowState state) {
        state.setAiMessage(message(state, ChatMessage.ROLE_AI, aiJson(state)));
        messageMapper.insert(state.getAiMessage());
        if (state.getOptionCard() != null) {
            // 仅挂起需要 checkpoint：选项卡选择/超时兑底从「停止之前的记忆」续跑
            checkpointService.save(state);
        }
        if (state.isReviseMode() || ChatFlowState.INTENT_REVISE.equals(state.getIntent())) {
            sessionMapper.incrReviseCount(state.tenantId(), state.getSession().getId());
            memoryService.track(state.tenantId(), "version_adopted");
        }
        state.setReply(reply(state.getSession(), state.getAiMessage(), state.getContextJson()));
    }

    /** AI 消息 JSON：动作 + 话术 + 版本 + 选项卡载荷 + 微调来源 + AI 代选标注。 */
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
            if (state.getOptionCard() != null) {
                payload.put("optionCard", state.getOptionCard());
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

    /** 回复 VO 组装（前端契约）。 */
    private com.xiaoa.ai.chat.dto.ChatReplyVO reply(ChatSession session, ChatMessage aiMessage, String context) {
        com.xiaoa.ai.chat.dto.ChatReplyVO vo = new com.xiaoa.ai.chat.dto.ChatReplyVO();
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
            if (node.hasNonNull("optionCard") && !node.path("optionCard").isNull()) {
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

    private List<String> stringList(JsonNode array) {
        List<String> list = new ArrayList<String>();
        if (array != null && array.isArray()) {
            array.forEach(item -> list.add(item.asText("")));
        }
        return list.isEmpty() ? null : list;
    }
}
