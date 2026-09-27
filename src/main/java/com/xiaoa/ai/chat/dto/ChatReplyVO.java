package com.xiaoa.ai.chat.dto;

import java.util.List;

/**
 * 对话回复。action=ASK 时 question 有值、versions 为空；
 * action=GENERATE 时 versions 为 3 版文案（微调返回 1 版新文案）。
 */
public class ChatReplyVO {

    private Long sessionId;
    private String action;
    private String question;
    private List<String> versions;
    /** 合并后的最新要素上下文 JSON，前端可展示当前收集进度 */
    private String context;
    private Long messageId;

    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
    public List<String> getVersions() { return versions; }
    public void setVersions(List<String> versions) { this.versions = versions; }
    public String getContext() { return context; }
    public void setContext(String context) { this.context = context; }
    public Long getMessageId() { return messageId; }
    public void setMessageId(Long messageId) { this.messageId = messageId; }
}
