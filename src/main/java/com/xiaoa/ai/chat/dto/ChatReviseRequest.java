package com.xiaoa.ai.chat.dto;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/** 微调指定版本：基于该版本 + 修改指令 → LLM 改写，小额扣费。 */
public class ChatReviseRequest {

    @NotNull(message = "版本号不能为空")
    @Min(value = 1, message = "版本号从1开始")
    @Max(value = 10, message = "版本号不合法")
    private Integer versionNo;

    @NotBlank(message = "修改指令不能为空")
    @Size(max = 1000, message = "修改指令长度不能超过1000")
    private String instruction;

    public Integer getVersionNo() { return versionNo; }
    public void setVersionNo(Integer versionNo) { this.versionNo = versionNo; }
    public String getInstruction() { return instruction; }
    public void setInstruction(String instruction) { this.instruction = instruction; }
}
