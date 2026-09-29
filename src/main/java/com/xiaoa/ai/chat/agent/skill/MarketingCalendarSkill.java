package com.xiaoa.ai.chat.agent.skill;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoa.ai.chat.graph.ChatFlowState;
import com.xiaoa.ai.chat.service.MarketingCalendarService;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 营销日历技能（queryCalendar）：查询未来 30 天营销节点与最近节日，
 * 供生成前注入节日营销元素。
 */
@Component
public class MarketingCalendarSkill implements Skill {

    private final MarketingCalendarService marketingCalendarService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MarketingCalendarSkill(MarketingCalendarService marketingCalendarService) {
        this.marketingCalendarService = marketingCalendarService;
    }

    @Override
    public String name() {
        return "queryCalendar";
    }

    @Override
    public String description() {
        return "查询未来 30 天营销节点与最近节日（如 520、七夕）";
    }

    @Override
    public String invoke(ChatFlowState state, com.fasterxml.jackson.databind.JsonNode args) {
        Map<String, Object> calendar = new LinkedHashMap<String, Object>();
        calendar.put("upcoming30Days", marketingCalendarService.upcoming30Days());
        calendar.put("nearest", marketingCalendarService.nearest());
        try {
            return objectMapper.writeValueAsString(calendar);
        } catch (Exception exception) {
            return "{\"error\":\"营销日历序列化失败\"}";
        }
    }
}
