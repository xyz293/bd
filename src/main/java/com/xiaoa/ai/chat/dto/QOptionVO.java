package com.xiaoa.ai.chat.dto;

/** 问卷选项（对齐方案 ③ compose_questionnaire 的选项 schema）。 */
public class QOptionVO {

    /** 选项标识：A/B/C/D */
    private String key;
    /** 展示文案：「对戒（520热销）」 */
    private String label;
    /** 辅助说明：「婚恋人群，情感向文案」 */
    private String hint;

    public QOptionVO() {
    }

    public QOptionVO(String key, String label, String hint) {
        this.key = key;
        this.label = label;
        this.hint = hint;
    }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getHint() { return hint; }
    public void setHint(String hint) { this.hint = hint; }
}
