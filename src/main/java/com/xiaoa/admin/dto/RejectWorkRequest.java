package com.xiaoa.admin.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

public class RejectWorkRequest {

    @NotBlank(message = "驳回意见不能为空")
    @Size(max = 512, message = "驳回意见长度不能超过512")
    private String opinion;

    public String getOpinion() { return opinion; }
    public void setOpinion(String opinion) { this.opinion = opinion; }
}
