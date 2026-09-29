package com.xiaoa.ai.chat.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoa.ai.chat.graph.ChatFlowState;
import com.xiaoa.ai.chat.mapper.ChatMessageMapper;
import com.xiaoa.ai.chat.model.ChatMessage;
import com.xiaoa.ai.chat.model.ChatSession;
import com.xiaoa.ai.chat.service.ChatMemoryService;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 上下文装配（① 取数骨架，供 ContextAgent 与恢复节点复用）：
 * Redis 会话槽位优先（断点续聊），miss 回退 MySQL 会话快照；附最近历史。
 */
@Component
public class ContextLoader {

    /** 携带给 LLM 的最近消息条数（10 轮 = 20 条） */
    private static final int HISTORY_MESSAGES = 20;

    private final ChatMessageMapper messageMapper;
    private final ChatMemoryService memoryService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ContextLoader(ChatMessageMapper messageMapper, ChatMemoryService memoryService) {
        this.messageMapper = messageMapper;
        this.memoryService = memoryService;
    }

    /** 装配槽位快照 + 问卷轮次 + 最近历史（已存在的历史不覆盖）。 */
    public void load(ChatFlowState state) {
        ChatSession session = state.getSession();
        String slots = memoryService.loadSlots(session.getId());
        state.setContextJson(slots != null ? slots : defaultContext(session));
        state.setQuestionnaireRound(memoryService.loadQuestionnaireRound(session.getId()));
        if (state.getHistoryJson() == null) {
            List<ChatMessage> recent = messageMapper.findRecent(session.getId(), HISTORY_MESSAGES);
            Collections.reverse(recent);
            state.setHistoryJson(historyJson(recent));
        }
    }

    /** 会话上下文兜底：MySQL 快照为空时给空对象。 */
    public String defaultContext(ChatSession session) {
        return session.getContext() == null || session.getContext().trim().isEmpty()
                ? "{}" : session.getContext();
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
}
