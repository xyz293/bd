package com.xiaoa.ai.chat.agent;

import com.xiaoa.ai.chat.graph.ChatFlowState;
import com.xiaoa.ai.chat.service.MarketingCalendarService;
import org.springframework.stereotype.Component;

/**
 * ① 上下文 Agent（fetchContext 节点）：
 * 一次取齐会话槽位记忆（Redis 优先，miss 回退 MySQL）、最近历史与营销日历，
 * 后续节点只读不再查；不调用 LLM。
 *
 * <p>隔离记忆：记录本次装配的营销日历，供 Gate/生成观测。</p>
 */
@Component
public class ContextAgent extends BaseNodeAgent {

    private final ContextLoader contextLoader;
    private final MarketingCalendarService marketingCalendarService;

    public ContextAgent(AgentMemoryService agentMemory, ContextLoader contextLoader,
                        MarketingCalendarService marketingCalendarService) {
        super(agentMemory);
        this.contextLoader = contextLoader;
        this.marketingCalendarService = marketingCalendarService;
    }

    @Override
    public String name() {
        return "contextAgent";
    }

    @Override
    public String systemPrompt() {
        return "数据装配 Agent：一次性取齐会话槽位记忆（Redis 优先，miss 回退 MySQL）、最近对话历史与营销日历；不调用 LLM，只读数据。";
    }

    @Override
    public void invoke(ChatFlowState state) {
        contextLoader.load(state);
        state.setCalendarFestivals(marketingCalendarService.upcoming30Days());
        remember(state, "calendar", jsonList(state.getCalendarFestivals()));
    }
}
