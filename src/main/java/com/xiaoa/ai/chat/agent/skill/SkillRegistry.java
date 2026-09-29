package com.xiaoa.ai.chat.agent.skill;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 技能注册表：收集所有 {@link Skill} Bean，供 Gate 校验 needSkills（LLM 不得发明技能）
 * 与 SkillAgent 按名执行；无名称命中时按用户输入关键词兜底匹配。
 */
@Component
public class SkillRegistry {

    private final Map<String, Skill> skillMap = new LinkedHashMap<String, Skill>();

    public SkillRegistry(List<Skill> skills) {
        for (Skill skill : skills) {
            skillMap.put(skill.name(), skill);
        }
    }

    /** 校验 Gate 给出的技能名：只保留注册过的，去重保序。 */
    public List<String> filterValid(List<String> names) {
        List<String> valid = new ArrayList<String>();
        if (names == null) {
            return valid;
        }
        for (String name : names) {
            if (name == null) {
                continue;
            }
            String trimmed = name.trim();
            if (skillMap.containsKey(trimmed) && !valid.contains(trimmed)) {
                valid.add(trimmed);
            }
        }
        return valid;
    }

    /**
     * 按名称选取技能执行；为空时按用户输入关键词兜底（商品词 → 商品资料，节日词 → 日历）。
     */
    public List<Skill> select(List<String> names, String userText) {
        List<Skill> selected = new ArrayList<Skill>();
        for (String name : filterValid(names)) {
            selected.add(skillMap.get(name));
        }
        if (!selected.isEmpty()) {
            return selected;
        }
        String text = userText == null ? "" : userText;
        if (text.matches(".*(节日|节点|活动|假日|纪念日).*") && skillMap.containsKey("queryCalendar")) {
            selected.add(skillMap.get("queryCalendar"));
        }
        if (text.matches(".*(商品|产品|款|戒指|项链|手镯|吊坠|耳饰|销售|卖点).*") && skillMap.containsKey("queryProduct")) {
            selected.add(skillMap.get("queryProduct"));
        }
        return selected;
    }

    /** 已注册技能目录（name=description），可拼进 Gate 提示词。 */
    public String catalog() {
        StringBuilder catalog = new StringBuilder();
        for (Skill skill : skillMap.values()) {
            if (catalog.length() > 0) {
                catalog.append("；");
            }
            catalog.append(skill.name()).append("=").append(skill.description());
        }
        return catalog.toString();
    }
}
