package com.xiaoa.ai.chat.agent;

import com.xiaoa.ai.chat.dto.OptionCardVO;
import com.xiaoa.ai.chat.graph.ChatFlowState;
import com.xiaoa.ai.chat.mapper.ChatMessageMapper;
import com.xiaoa.ai.chat.mapper.ChatSessionMapper;
import com.xiaoa.ai.chat.model.ChatMessage;
import com.xiaoa.ai.chat.model.ChatSession;
import com.xiaoa.ai.chat.service.ChatCheckpointService;
import com.xiaoa.ai.chat.service.ChatMemoryService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 持久化 Agent（persist 节点）：
 * AI 消息落库 + MySQL 会话槽位快照刷新 + Redis 会话记忆同步（断点续聊）
 * + 图状态短期记忆 checkpoint（thread id = 会话 ID）+ 微调计数与埋点。
 *
 * <p>隔离记忆：记录本轮落库的消息 ID。</p>
 */
@Component
public class PersistAgent extends BaseNodeAgent {

    private final ChatMessageMapper messageMapper;
    private final ChatSessionMapper sessionMapper;
    private final ChatMemoryService memoryService;
    private final ChatCheckpointService checkpointService;

    public PersistAgent(AgentMemoryService agentMemory, ChatMessageMapper messageMapper,
                        ChatSessionMapper sessionMapper, ChatMemoryService memoryService,
                        ChatCheckpointService checkpointService) {
        super(agentMemory);
        this.messageMapper = messageMapper;
        this.sessionMapper = sessionMapper;
        this.memoryService = memoryService;
        this.checkpointService = checkpointService;
    }

    @Override
    public String name() {
        return "persistAgent";
    }

    @Override
    public String systemPrompt() {
        return "持久化 Agent：AI 消息落库、会话槽位同步 MySQL/Redis、图状态 checkpoint 落 Redis、微调计数与埋点；只写不决策。";
    }

    @Override
    public void invoke(ChatFlowState state) {
        messageMapper.insert(state.getAiMessage());
        sessionMapper.updateContext(state.tenantId(), state.getSession().getId(), state.getContextJson());
        memoryService.saveSlots(state.getSession().getId(), state.getContextJson());
        checkpointService.save(state);
        if (state.getMode() == ChatFlowState.Mode.REVISE
                || ChatFlowState.INTENT_REVISE.equals(state.getIntent())) {
            sessionMapper.incrReviseCount(state.tenantId(), state.getSession().getId());
            memoryService.track(state.tenantId(), "version_adopted");
        }
        state.setReply(reply(state.getSession(), state.getAiMessage(), state.getContextJson()));
        remember(state, "messageId", String.valueOf(state.getAiMessage().getId()));
    }

    // ==================== 回复 VO 组装 ====================

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
