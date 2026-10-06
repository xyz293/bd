package com.xiaoa.ai.chat.provider;

/**
 * LLM 请求。mode 对应图节点职责：
 * CHAT 对话出稿 / REVISE 基于指定版本改写 / GATE 充分性判断（HITL） /
 * OPTIONS 选项卡生成（出题维度/题干/选项全由 LLM 思考决定） / COMPOSE 口语化回复组装 /
 * REACT 技能收集的思考-行动循环（SkillAgent，每轮一条 thought/action/observation）。
 * historyJson 为最近若干轮 [{"role":"USER|AI","content":"..."}]；
 * messages 为 REACT 多轮轨迹（仅 REACT 模式使用，非空时替代单条 user 消息）；
 * systemPrompt 由发起请求的 Agent 携带自己的提示词（YAML xiaoa.ai.llm.prompts 可配），
 * Provider 优先使用，缺省按 mode 回退内置默认。
 */
public class LlmRequest {

    public static final String MODE_CHAT = "CHAT";
    public static final String MODE_REVISE = "REVISE";
    public static final String MODE_GATE = "GATE";
    public static final String MODE_OPTIONS = "OPTIONS";
    public static final String MODE_COMPOSE = "COMPOSE";
    public static final String MODE_REACT = "REACT";

    private String mode;
    private String scene;
    private String contextJson;
    private String historyJson;
    /** CHAT：本轮员工输入；REVISE：修改指令 */
    private String userText;
    /** REVISE：被改写的原文案 */
    private String baseText;
    /** REACT：多轮轨迹（初始任务 + assistant/action + user/observation 交替），非空时替代单条 user 消息 */
    private java.util.List<java.util.Map<String, String>> messages;
    /** 系统提示词：Agent 随请求携带自己的提示词（YAML xiaoa.ai.llm.prompts.<agent> 可配）；空则 Provider 按 mode 回退内置默认 */
    private String systemPrompt;

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

    /**
     * 选项卡生成（OptionAgent）：出题维度/题干/选项全由 LLM 思考决定——
     * 输入为已确认槽位 + 已问维度 + Gate 缺口（userText 载荷），LLM 决定下一个最有用的维度。
     */
    public static LlmRequest options(String scene, String contextJson, String missingJson, String userText) {
        LlmRequest request = new LlmRequest();
        request.mode = MODE_OPTIONS;
        request.scene = scene;
        request.contextJson = contextJson;
        request.userText = missingJson;
        request.baseText = userText;
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

    /**
     * ReAct 技能收集（SkillAgent）：messages 为完整轨迹（首条为任务描述，
     * 之后 assistant{thought,action} 与 user{observation} 交替），每轮追加一条再调一次。
     */
    public static LlmRequest react(String scene, String contextJson,
                                   java.util.List<java.util.Map<String, String>> messages) {
        LlmRequest request = new LlmRequest();
        request.mode = MODE_REACT;
        request.scene = scene;
        request.contextJson = contextJson;
        request.messages = messages;
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
    public java.util.List<java.util.Map<String, String>> getMessages() { return messages; }
    public void setMessages(java.util.List<java.util.Map<String, String>> messages) { this.messages = messages; }
    public String getSystemPrompt() { return systemPrompt; }
    public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }
}
