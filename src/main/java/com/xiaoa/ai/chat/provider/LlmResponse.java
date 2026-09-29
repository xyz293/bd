package com.xiaoa.ai.chat.provider;

import com.xiaoa.ai.chat.dto.QuestionVO;

import java.util.List;

/**
 * LLM 结构化返回：action=ASK 追问（question）；action=GENERATE 出稿（versions）。
 * contextPatchJson 为要素补丁，由服务层合并回 session.context。
 *
 * <p>图节点扩展字段：intent/taskType（意图识别）、questions（动态问卷）、
 * ready/options（HITL 候选选项）。</p>
 */
public class LlmResponse {

    public static final String ACTION_ASK = "ASK";
    public static final String ACTION_GENERATE = "GENERATE";

    /** 意图：新创作 / 微调 / 咨询（intent_router 节点返回，对齐方案 ② 三分类） */
    public static final String INTENT_NEW_CREATE = "NEW_CREATE";
    public static final String INTENT_REVISE = "REVISE";
    public static final String INTENT_CONSULT = "CONSULT";

    /** 任务类型：COPY 文案 / IMAGE 配图 / VIDEO 视频 */
    public static final String TASK_COPY = "COPY";
    public static final String TASK_IMAGE = "IMAGE";
    public static final String TASK_VIDEO = "VIDEO";

    private String action;
    private String question;
    private String contextPatchJson;
    private List<String> versions;
    private String intent;
    /** 任务类型：COPY 文案 / IMAGE 配图 / VIDEO 视频（意图识别输出） */
    private String taskType;
    /** 动态问卷题目（intent 模式之外的独立模式输出） */
    private List<QuestionVO> questions;
    private Boolean ready;
    private List<String> options;
    /** GATE：信息缺口描述（ready=false 时给出） */
    private List<String> missing;
    /** GATE：生成前需要调用的技能名（由 SkillRegistry 校验） */
    private List<String> needSkills;
    /** GATE：判定理由（观测） */
    private String reason;

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

    /** 意图识别结果（intent_router 节点） */
    public static LlmResponse intentOf(String intent) {
        LlmResponse response = new LlmResponse();
        response.action = ACTION_ASK;
        response.intent = intent;
        return response;
    }

    /** 槽位完整度判定 + 候选选项（slot_gate 节点，HITL） */
    public static LlmResponse gate(Boolean ready, List<String> options) {
        LlmResponse response = new LlmResponse();
        response.action = ACTION_ASK;
        response.ready = ready;
        response.options = options;
        return response;
    }

    /** 口语化回复（response_composer / 闲聊节点） */
    public static LlmResponse compose(String question) {
        LlmResponse response = new LlmResponse();
        response.action = ACTION_ASK;
        response.question = question;
        return response;
    }

    /** 动态问卷题目（compose_questionnaire 节点输出） */
    public static LlmResponse questionsOf(List<QuestionVO> questions) {
        LlmResponse response = new LlmResponse();
        response.action = ACTION_ASK;
        response.questions = questions;
        return response;
    }

    /** 槽位抽取结果（slot_extractor 节点，仅携带 contextPatch） */
    public static LlmResponse slotOf(String contextPatchJson) {
        LlmResponse response = new LlmResponse();
        response.action = ACTION_GENERATE;
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
    public String getIntent() { return intent; }
    public void setIntent(String intent) { this.intent = intent; }
    public String getTaskType() { return taskType; }
    public void setTaskType(String taskType) { this.taskType = taskType; }
    public List<QuestionVO> getQuestions() { return questions; }
    public void setQuestions(List<QuestionVO> questions) { this.questions = questions; }
    public Boolean getReady() { return ready; }
    public void setReady(Boolean ready) { this.ready = ready; }
    public List<String> getOptions() { return options; }
    public void setOptions(List<String> options) { this.options = options; }
    public List<String> getMissing() { return missing; }
    public void setMissing(List<String> missing) { this.missing = missing; }
    public List<String> getNeedSkills() { return needSkills; }
    public void setNeedSkills(List<String> needSkills) { this.needSkills = needSkills; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
