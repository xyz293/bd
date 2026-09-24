package com.xiaoa.ai.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

public class UpdateWorkCaptionRequest {

    @NotBlank(message = "文案不能为空")
    @Size(max = 2000, message = "文案长度不能超过2000")
    private String caption;

    public String getCaption() { return caption; }
    public void setCaption(String caption) { this.caption = caption; }
}
