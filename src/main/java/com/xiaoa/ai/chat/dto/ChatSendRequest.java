package com.xiaoa.ai.chat.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/** 发送一句话（核心对话接口）。 */
public class ChatSendRequest {

    @NotBlank(message = "消息内容不能为空")
    @Size(max = 2000, message = "消息内容长度不能超过2000")
    private String text;

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
}
