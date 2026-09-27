package com.xiaoa.ai.chat.provider;

import java.util.List;

/**
 * LLM 结构化返回：action=ASK 追问（question）；action=GENERATE 出稿（versions）。
 * contextPatchJson 为要素补丁，由服务层合并回 session.context。
 */
public class LlmResponse {

    public static final String ACTION_ASK = "ASK";
    public static final String ACTION_GENERATE = "GENERATE";

    private String action;
    private String question;
    private String contextPatchJson;
    private List<String> versions;

    public static LlmResponse ask(String question, String contextPatchJson) {
        LlmResponse response = new LlmResponse();
        response.action = ACTION_ASK;
        response.question = question;
        response.contextPatchJson = contextPatchJson;
        return response;
    }

    public static LlmResponse generate(List<String> versions, String contextPatchJson) {
        LlmResponse response = new LlmResponse();
        response.action = ACTION_GENERATE;
        response.versions = versions;
        response.contextPatchJson = contextPatchJson;
        return response;
    }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
    public String getContextPatchJson() { return contextPatchJson; }
    public void setContextPatchJson(String contextPatchJson) { this.contextPatchJson = contextPatchJson; }
    public List<String> getVersions() { return versions; }
    public void setVersions(List<String> versions) { this.versions = versions; }
}
