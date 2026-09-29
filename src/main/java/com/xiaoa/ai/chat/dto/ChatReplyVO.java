package com.xiaoa.ai.chat.dto;

import java.util.List;

/**
 * 对话回复（节点推进结果，方案 §9 前后端契约）：
 * <ul>
 *   <li>ASK：追问/咨询回复，question 有值</li>
 *   <li>GENERATE：出稿，versions 为文案版本（微调返回 1 版），question 为三段式事实说明</li>
 *   <li>QUESTIONNAIRE：问卷挂起，questionnaire 为 1~3 道选择题，前端渲染问卷并等待作答</li>
 *   <li>OPTION_CARD：选项卡挂起，optionCard 为 A/B/C/D 增益选项，前端 30s 倒计时，超时不提交</li>
 *   <li>PENDING_MEDIA：图/视频挂起，workId 供前端轮询作品状态</li>
 * </ul>
 */
public class ChatReplyVO {

    private Long sessionId;
    private String action;
    private String question;
    private List<String> versions;
    /** HITL 候选选项（旧快捷回复，保留兼容），新流程以 questionnaire/optionCard 为主 */
    private List<String> options;
    /** 问卷（action=QUESTIONNAIRE） */
    private List<QuestionVO> questionnaire;
    /** 选项卡（action=OPTION_CARD） */
    private OptionCardVO optionCard;
    /** 异步媒体作品 ID（action=PENDING_MEDIA） */
    private Long workId;
    /** 合并后的最新槽位上下文 JSON，前端可展示当前收集进度 */
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
    public List<String> getOptions() { return options; }
    public void setOptions(List<String> options) { this.options = options; }
    public List<QuestionVO> getQuestionnaire() { return questionnaire; }
    public void setQuestionnaire(List<QuestionVO> questionnaire) { this.questionnaire = questionnaire; }
    public OptionCardVO getOptionCard() { return optionCard; }
    public void setOptionCard(OptionCardVO optionCard) { this.optionCard = optionCard; }
    public Long getWorkId() { return workId; }
    public void setWorkId(Long workId) { this.workId = workId; }
    public String getContext() { return context; }
    public void setContext(String context) { this.context = context; }
    public Long getMessageId() { return messageId; }
    public void setMessageId(Long messageId) { this.messageId = messageId; }
}
