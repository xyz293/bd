package com.xiaoa.ai.chat.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/** 创建对话会话请求：入口选定创作场景。 */
public class ChatSessionCreateRequest {

    @NotBlank(message = "创作场景不能为空")
    @Size(max = 32, message = "创作场景长度不能超过32")
    private String scene;

    public String getScene() { return scene; }
    public void setScene(String scene) { this.scene = scene; }
}
