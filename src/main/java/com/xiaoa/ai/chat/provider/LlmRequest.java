package com.xiaoa.ai.chat.provider;

/**
 * LLM 请求。mode=CHAT 对话收集要素/出稿；mode=REVISE 基于指定版本改写。
 * historyJson 为最近若干轮 [{"role":"USER|AI","content":"..."}]。
 */
public class LlmRequest {

    public static final String MODE_CHAT = "CHAT";
    public static final String MODE_REVISE = "REVISE";

    private String mode;
    private String scene;
    private String contextJson;
    private String historyJson;
    /** CHAT：本轮员工输入；REVISE：修改指令 */
    private String userText;
    /** REVISE：被改写的原文案 */
    private String baseText;

    public static LlmRequest chat(String scene, String contextJson, String historyJson, String userText) {
        LlmRequest request = new LlmRequest();
        request.mode = MODE_CHAT;
        request.scene = scene;
        request.contextJson = contextJson;
        request.historyJson = historyJson;
        request.userText = userText;
        return request;
    }

    public static LlmRequest revise(String scene, String contextJson, String baseText, String instruction) {
        LlmRequest request = new LlmRequest();
        request.mode = MODE_REVISE;
        request.scene = scene;
        request.contextJson = contextJson;
        request.baseText = baseText;
        request.userText = instruction;
        return request;
    }

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public String getScene() { return scene; }
    public void setScene(String scene) { this.scene = scene; }
    public String getContextJson() { return contextJson; }
    public void setContextJson(String contextJson) { this.contextJson = contextJson; }
    public String getHistoryJson() { return historyJson; }
    public void setHistoryJson(String historyJson) { this.historyJson = historyJson; }
    public String getUserText() { return userText; }
    public void setUserText(String userText) { this.userText = userText; }
    public String getBaseText() { return baseText; }
    public void setBaseText(String baseText) { this.baseText = baseText; }
}
