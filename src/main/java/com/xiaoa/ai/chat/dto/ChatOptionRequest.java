package com.xiaoa.ai.chat.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

/**
 * 选项卡选择提交（方案 ⑨ /chat/option）：A/B/C/D 单选；
 * 不选/超时不调本接口，由后端兜底任务按「直接生成」放行。
 */
public class ChatOptionRequest {

    @NotBlank(message = "选项不能为空")
    @Pattern(regexp = "^[A-Z]$", message = "选项只能是 A/B/C/D")
    private String key;

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
}
