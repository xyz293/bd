package com.xiaoa.ai.chat.dto;

import java.util.List;

/**
 * 问卷题目（方案 ③ compose_questionnaire 输出 schema）：
 * 针对 missing 必填项动态出题，每题 2~4 选项，支持「AI 代选」。
 */
public class QuestionVO {

    /** 题目 ID：q_product */
    private String id;
    /** 对应槽位 key：product / platform / tone…（必须在预注册槽位字典内） */
    private String slotKey;
    /** 题干：「这次推哪个商品？」 */
    private String question;
    /** 选项（2~4 个） */
    private List<QOptionVO> options;
    /** 最多可选数（一期固定 1） */
    private Integer maxSelect = 1;
    /** 是否允许「你帮我定」（AI 代选） */
    private Boolean allowAiDecide = true;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSlotKey() { return slotKey; }
    public void setSlotKey(String slotKey) { this.slotKey = slotKey; }
    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
    public List<QOptionVO> getOptions() { return options; }
    public void setOptions(List<QOptionVO> options) { this.options = options; }
    public Integer getMaxSelect() { return maxSelect; }
    public void setMaxSelect(Integer maxSelect) { this.maxSelect = maxSelect; }
    public Boolean getAllowAiDecide() { return allowAiDecide; }
    public void setAllowAiDecide(Boolean allowAiDecide) { this.allowAiDecide = allowAiDecide; }
}
