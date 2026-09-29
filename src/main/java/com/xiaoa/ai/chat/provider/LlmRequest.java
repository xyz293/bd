package com.xiaoa.ai.chat.provider;

/**
 * LLM 请求。mode 对应图节点职责：
 * CHAT 对话出稿 / REVISE 基于指定版本改写 / INTENT 意图识别 + 槽位抽取 /
 * QUESTIONNAIRE 动态问卷 / GATE 槽位完整度判断（HITL） / COMPOSE 口语化回复组装。
 * historyJson 为最近若干轮 [{"role":"USER|AI","content":"..."}]。
 */
public class LlmRequest {

    public static final String MODE_CHAT = "CHAT";
    public static final String MODE_REVISE = "REVISE";
    public static final String MODE_INTENT = "INTENT";
    public static final String MODE_GATE = "GATE";
    public static final String MODE_COMPOSE = "COMPOSE";
    public static final String MODE_QUESTIONNAIRE = "QUESTIONNAIRE";

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

    /**
     * 意图识别 + 槽位抽取（方案 ② understand_intent，一次调用同时完成）：
     * 输出 intent/taskType/slots，缺口由代码按预注册槽位字典计算（不靠 LLM 算，管不失控）。
     */
    public static LlmRequest intent(String scene, String contextJson, String historyJson, String userText) {
        LlmRequest request = new LlmRequest();
        request.mode = MODE_INTENT;
        request.scene = scene;
        request.contextJson = contextJson;
        request.historyJson = historyJson;
        request.userText = userText;
        return request;
    }

    /** 槽位完整度判断（HITL）：大模型自判信息够不够，不够时给候选选项。 */
    public static LlmRequest gate(String scene, String contextJson, String historyJson, String userText) {
        LlmRequest request = new LlmRequest();
        request.mode = MODE_GATE;
        request.scene = scene;
        request.contextJson = contextJson;
        request.historyJson = historyJson;
        request.userText = userText;
        return request;
    }

    /** 口语化回复组装：闲聊直接回复 / 出稿引导语包装。 */
    public static LlmRequest compose(String scene, String contextJson, String historyJson, String userText) {
        LlmRequest request = new LlmRequest();
        request.mode = MODE_COMPOSE;
        request.scene = scene;
        request.contextJson = contextJson;
        request.historyJson = historyJson;
        request.userText = userText;
        return request;
    }

    /** 动态问卷：针对 missing 必填项出 1~3 道选择题（方案 ③）。 */
    public static LlmRequest questionnaire(String scene, String contextJson, String missingJson) {
        LlmRequest request = new LlmRequest();
        request.mode = MODE_QUESTIONNAIRE;
        request.scene = scene;
        request.contextJson = contextJson;
        request.userText = missingJson;
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
