package com.xiaoa.ai.chat.service;

import com.xiaoa.ai.chat.mapper.ChatMessageMapper;
import com.xiaoa.ai.chat.mapper.ChatSessionMapper;
import com.xiaoa.ai.chat.model.ChatMessage;
import com.xiaoa.ai.chat.model.ChatSession;
import com.xiaoa.common.auth.AuthPrincipal;
import com.xiaoa.common.exception.BusinessException;
import com.xiaoa.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 会话 CRUD：创建（带场景）/ 列表 / 全量历史（重进页面恢复）。
 * 7 天不活跃的会话在下次发消息时懒关闭，防止无限长会话。
 */
@Service
public class ChatSessionService {

    /** 会话不活跃自动关闭天数 */
    private static final int IDLE_CLOSE_DAYS = 7;

    private final ChatSessionMapper sessionMapper;
    private final ChatMessageMapper messageMapper;

    public ChatSessionService(ChatSessionMapper sessionMapper, ChatMessageMapper messageMapper) {
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
    }

    @Transactional
    public ChatSession create(AuthPrincipal principal, String scene) {
        ChatSession session = new ChatSession();
        session.setTenantId(principal.getTenantId());
        session.setUserId(principal.getUserId());
        session.setScene(scene == null || scene.trim().isEmpty() ? "朋友圈" : scene.trim());
        session.setTitle(session.getScene() + " · 创作对话");
        session.setStatus(ChatSession.STATUS_ACTIVE);
        session.setContext("{}");
        sessionMapper.insert(session);
        return session;
    }

    public List<ChatSession> listMine(AuthPrincipal principal) {
        return sessionMapper.findByUser(principal.getTenantId(), principal.getUserId(), 20);
    }

    /** 全量历史（本人校验），消息按时间正序返回 */
    public ChatSession history(AuthPrincipal principal, Long sessionId) {
        ChatSession session = requireOwned(principal, sessionId);
        return session;
    }

    public List<ChatMessage> messages(AuthPrincipal principal, Long sessionId) {
        requireOwned(principal, sessionId);
        return messageMapper.findBySession(sessionId);
    }

    /**
     * 会话可用性校验：本人、未关闭、7 天内活跃（超期懒关闭）。
     */
    public ChatSession requireUsable(AuthPrincipal principal, Long sessionId) {
        ChatSession session = requireOwned(principal, sessionId);
        if (ChatSession.STATUS_CLOSED.equals(session.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "会话已关闭，请新建会话");
        }
        if (session.getUpdatedAt() != null
                && session.getUpdatedAt().isBefore(LocalDateTime.now().minusDays(IDLE_CLOSE_DAYS))) {
            sessionMapper.close(principal.getTenantId(), session.getId());
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "会话超过" + IDLE_CLOSE_DAYS + "天未活跃已关闭，请新建会话");
        }
        return session;
    }

    private ChatSession requireOwned(AuthPrincipal principal, Long sessionId) {
        if (sessionId == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "会话不存在");
        }
        ChatSession session = sessionMapper.findById(principal.getTenantId(), sessionId);
        if (session == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "会话不存在");
        }
        if (!principal.getUserId().equals(session.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能访问自己的会话");
        }
        return session;
    }
}
