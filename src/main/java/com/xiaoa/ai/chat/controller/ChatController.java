package com.xiaoa.ai.chat.controller;

import com.xiaoa.ai.chat.dto.ChatAnswerRequest;
import com.xiaoa.ai.chat.dto.ChatOptionRequest;
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
 * 对话模式（引导式聊天创作，节点编排定稿方案）：员工说人话，AI 问卷引导补齐信息，
 * 核验+预扣额度 → 选项卡确认 → 出稿 → 合规审查 → 三段式回复。
 *
 * <p>接口契约（方案 §9，路径挂现有 /api/chat 体系）：</p>
 * <ul>
 *   <li>POST /sessions/{id}/messages —— 用户消息入口，返回节点推进结果
 *       （问卷 QUESTIONNAIRE / 选项卡 OPTION_CARD / 生成结果 GENERATE / 媒体挂起 PENDING_MEDIA）</li>
 *   <li>POST /sessions/{id}/answer —— 问卷作答提交（挂起点1 恢复）</li>
 *   <li>POST /sessions/{id}/option —— 选项卡选择 A/B/C/D（挂起点2 恢复，超时由后端兑底）</li>
 *   <li>GET  /sessions/{id} —— 恢复会话（断点续聊，消息里含挂起问卷/选项卡）</li>
 *   <li>POST /sessions/{id}/revise —— 微调指定版本</li>
 * </ul>
 * 计费：预扣（chat:{sessionId}:hold:{seq}）→ 成功确认 / 失败随事务回滚自动释放；微调 1 点（rev:{n}）。
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

    /** 发一句话（核心对话接口）。流式版本见 WebSocket 通道 /ws/chat（ChatWebSocketHandler）。 */
    @PostMapping("/sessions/{id}/messages")
    public Result<ChatReplyVO> chat(@PathVariable Long id, @Valid @RequestBody ChatSendRequest request) {
        return Result.success(flowService.chat(AuthContext.required(), id, request));
    }

    /** 问卷作答提交（挂起点1 恢复：答案合并 → 回 ② 重算缺口，轮次 ≤2） */
    @PostMapping("/sessions/{id}/answer")
    public Result<ChatReplyVO> answer(@PathVariable Long id, @Valid @RequestBody ChatAnswerRequest request) {
        return Result.success(flowService.answer(AuthContext.required(), id, request));
    }

    /** 选项卡选择（挂起点2 恢复：应用增益槽位 → 直进 ⑥；不选/超时由后端按 D 兑底放行） */
    @PostMapping("/sessions/{id}/option")
    public Result<ChatReplyVO> option(@PathVariable Long id, @Valid @RequestBody ChatOptionRequest request) {
        return Result.success(flowService.option(AuthContext.required(), id, request));
    }

    /** 微调指定版本 */
    @PostMapping("/sessions/{id}/revise")
    public Result<ChatReplyVO> revise(@PathVariable Long id, @Valid @RequestBody ChatReviseRequest request) {
        return Result.success(flowService.revise(AuthContext.required(), id, request));
    }
}
