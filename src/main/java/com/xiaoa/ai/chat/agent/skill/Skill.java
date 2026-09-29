package com.xiaoa.ai.chat.agent.skill;

import com.fasterxml.jackson.databind.JsonNode;
import com.xiaoa.ai.chat.graph.ChatFlowState;

/**
 * 技能（Agent 可调用的取数工具）：Gate 判定需要什么资料，SkillAgent 按注册表执行。
 * 技能只负责客观取数，不做创作、不改写槽位，取回的资料供生成 Agent 拼装 prompt。
 */
public interface Skill {

    /** 技能名（Gate 的 needSkills 用它引用，如 "queryProduct"）。 */
    String name();

    /** 技能描述（供 Gate 提示词目录与观测）。 */
    String description();

    /** 执行技能，返回取回的资料（JSON 字符串）；失败抛异常由 SkillAgent 兜底。 */
    String invoke(ChatFlowState state, JsonNode args);
}
