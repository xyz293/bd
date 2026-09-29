package com.xiaoa.ai.chat.dto;

import java.util.List;

/**
 * 生成前选项卡（方案 ⑤ option_card）：A/B/C/D 增益选项，仅出现一次，
 * 前端 30s 计时，超时/不选默认「直接生成」，后端 60s 兜底放行。
 */
public class OptionCardVO {

    /** 选项（含必有的 D 直接生成） */
    private List<QOptionVO> options;
    /** 前端倒计时秒数 */
    private Integer deadlineSeconds = 30;

    public OptionCardVO() {
    }

    public OptionCardVO(List<QOptionVO> options, Integer deadlineSeconds) {
        this.options = options;
        this.deadlineSeconds = deadlineSeconds;
    }

    public List<QOptionVO> getOptions() { return options; }
    public void setOptions(List<QOptionVO> options) { this.options = options; }
    public Integer getDeadlineSeconds() { return deadlineSeconds; }
    public void setDeadlineSeconds(Integer deadlineSeconds) { this.deadlineSeconds = deadlineSeconds; }
}
