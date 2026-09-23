package com.xiaoa.task.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

public class PublishRecordRequest {

    @NotNull(message = "作品不能为空")
    private Long workId;
    private Long taskId;
    @NotBlank(message = "发布平台不能为空")
    private String platform;
    private String proofUrl;

    public Long getWorkId() { return workId; }
    public void setWorkId(Long workId) { this.workId = workId; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }
    public String getProofUrl() { return proofUrl; }
    public void setProofUrl(String proofUrl) { this.proofUrl = proofUrl; }
}
