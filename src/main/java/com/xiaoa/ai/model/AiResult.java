package com.xiaoa.ai.model;

public class AiResult {

    private Integer status;
    private String contentUrl;
    private String failCode;
    private String failReason;

    public static AiResult success(String contentUrl) {
        AiResult result = new AiResult();
        result.status = 1;
        result.contentUrl = contentUrl;
        return result;
    }

    public static AiResult processing() {
        AiResult result = new AiResult();
        result.status = 0;
        return result;
    }

    public static AiResult failure(String failCode, String failReason) {
        AiResult result = new AiResult();
        result.status = 2;
        result.failCode = failCode;
        result.failReason = failReason;
        return result;
    }

    public boolean isSuccess() {
        return Integer.valueOf(1).equals(status) && contentUrl != null && !contentUrl.trim().isEmpty();
    }

    public boolean isProcessing() {
        return Integer.valueOf(0).equals(status);
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getContentUrl() {
        return contentUrl;
    }

    public void setContentUrl(String contentUrl) {
        this.contentUrl = contentUrl;
    }

    public String getFailCode() {
        return failCode;
    }

    public void setFailCode(String failCode) {
        this.failCode = failCode;
    }

    public String getFailReason() {
        return failReason;
    }

    public void setFailReason(String failReason) {
        this.failReason = failReason;
    }
}
