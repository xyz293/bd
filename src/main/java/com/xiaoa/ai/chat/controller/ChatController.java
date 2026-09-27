package com.xiaoa.ai.chat.controller;

import com.xiaoa.ai.chat.dto.ChatReplyVO;
import com.xiaoa.ai.chat.dto.ChatReviseRequest;
import com.xiaoa.ai.chat.dto.ChatSendRequest;
import com.xiaoa.ai.chat.dto.ChatSessionCreateRequest;
import com.xiaoa.ai.chat.model.ChatMessage;
import com.xiaoa.ai.chat.model.ChatSession;
import com.xiaoa.ai.chat.service.ChatFlowService;
import com.xiaoa.ai.chat.service.ChatSessionService;
import com.xiaoa.common.api.Result;
import com.xiaoa.common.auth.AuthContext;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 对话模式（引导式聊天创作）：员工说人话，AI 追问补齐要素，一次出 3 版文案，可微调换版。
 * 计费：首次出稿 1 点（chat:{sessionId}:gen），微调 1 点（chat:{sessionId}:rev:{n}）。
 */
@RestController
@RequestMapping("/api/chat")
@Validated
public class ChatController {

    private final ChatSessionService sessionService;
    private final ChatFlowService flowService;

    public ChatController(ChatSessionService sessionService, ChatFlowService flowService) {
        this.sessionService = sessionService;
        this.flowService = flowService;
    }

    /** 创建会话（带场景） */
    @PostMapping("/sessions")
    public Result<ChatSession> create(@Valid @RequestBody ChatSessionCreateRequest request) {
        return Result.success(sessionService.create(AuthContext.required(), request.getScene()));
    }

    /** 会话列表 */
    @GetMapping("/sessions")
    public Result<List<ChatSession>> list() {
        return Result.success(sessionService.listMine(AuthContext.required()));
    }

    /** 全量历史（重进页面恢复） */
    @GetMapping("/sessions/{id}")
    public Result<Map<String, Object>> history(@PathVariable Long id) {
        ChatSession session = sessionService.history(AuthContext.required(), id);
        List<ChatMessage> messages = sessionService.messages(AuthContext.required(), id);
        Map<String, Object> payload = new HashMap<String, Object>();
        payload.put("session", session);
        payload.put("messages", messages);
        return Result.success(payload);
    }

    /** 发一句话（核心对话接口） */
    @PostMapping("/sessions/{id}/messages")
    public Result<ChatReplyVO> chat(@PathVariable Long id, @Valid @RequestBody ChatSendRequest request) {
        return Result.success(flowService.chat(id, request));
    }

    /** 微调指定版本 */
    @PostMapping("/sessions/{id}/revise")
    public Result<ChatReplyVO> revise(@PathVariable Long id, @Valid @RequestBody ChatReviseRequest request) {
        return Result.success(flowService.revise(id, request));
    }
}
