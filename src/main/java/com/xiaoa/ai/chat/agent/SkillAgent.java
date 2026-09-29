package com.xiaoa.ai.chat.agent;

import com.xiaoa.ai.chat.agent.skill.Skill;
import com.xiaoa.ai.chat.agent.skill.SkillRegistry;
import com.xiaoa.ai.chat.graph.ChatFlowState;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 技能调用 Agent（skillInvoke 节点）：
 * 按 Gate 判定的 needSkills 从技能注册表选取并执行技能，取回生成所需的客观资料
 * （商品资料/营销节点），落到 state.skillFacts 供生成 Agent 拼 prompt；
 * 只取数不创作，单技能失败不影响整体（记录失败继续）。
 *
 * <p>隔离记忆：调用轨迹（invocations）与取回事实（facts）。</p>
 */
@Component
public class SkillAgent extends BaseNodeAgent {

    private final SkillRegistry skillRegistry;

    public SkillAgent(AgentMemoryService agentMemory, SkillRegistry skillRegistry) {
        super(agentMemory);
        this.skillRegistry = skillRegistry;
    }

    @Override
    public String name() {
        return "skillAgent";
    }

    @Override
    public String systemPrompt() {
        return "技能调用 Agent：按 Gate 判定的 needSkills 从技能注册表执行技能，取回生成所需客观资料"
                + "（商品资料/营销节点）；只记录调用轨迹与取回事实，不做创作、不改写槽位。";
    }

    @Override
    public void invoke(ChatFlowState state) {
        List<Skill> selected = skillRegistry.select(state.getNeedSkills(), state.getUserInput());
        if (selected.isEmpty()) {
            state.setSkillFacts(null);
            remember(state, "invocations", "[]");
            return;
        }
        List<Map<String, String>> records = new ArrayList<Map<String, String>>();
        List<String> names = new ArrayList<String>();
        for (Skill skill : selected) {
            Map<String, String> record = new LinkedHashMap<String, String>();
            record.put("skill", skill.name());
            try {
                record.put("info", skill.invoke(state, null));
            } catch (RuntimeException exception) {
                // 单技能失败不阻塞生成：记录失败，资料缺失由事实核验兜底为占位符
                record.put("info", "查询失败");
            }
            records.add(record);
            names.add(skill.name());
        }
        try {
            String factsJson = objectMapper.writeValueAsString(records);
            state.setSkillFacts(factsJson);
            remember(state, "invocations", jsonList(names));
            remember(state, "facts", factsJson);
        } catch (Exception exception) {
            state.setSkillFacts(null);
            remember(state, "invocations", jsonList(names));
        }
    }
}
