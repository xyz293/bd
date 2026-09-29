package com.xiaoa.ai.chat.agent;

import com.xiaoa.ai.chat.agent.skill.SkillRegistry;
import com.xiaoa.ai.chat.graph.ChatFlowState;
import com.xiaoa.ai.chat.provider.LlmProvider;
import com.xiaoa.ai.chat.provider.LlmRequest;
import com.xiaoa.ai.chat.provider.LlmResponse;
import com.xiaoa.ai.chat.service.ChatMemoryService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 信息充分性判断 Agent（gateAssess 节点，HITL Gate 循环中枢）：
 * 根据提示词与上下文判断「现在能否直接生成」——
 * <ul>
 *   <li>足够 → 标记 ENOUGH，并给出生成前需要调用的技能（needSkills），交 SkillAgent 取资料后直进生成；</li>
 *   <li>不够 → 标记 NOT_ENOUGH，给出缺口与用户可点选的候选方向，交 OptionAgent 出选项卡挂起；
 *       用户选择后经 applyOption 合并回上下文再次进入本节点复判，循环直到足够。</li>
 * </ul>
 * 轮次封顶（3 轮）后 AI 代选兜底放行，保证流程必然收敛。
 *
 * <p>隔离记忆：判断轮次（round）与每轮判定结论（verdicts），只存本 Agent 业务产物。</p>
 */
@Component
public class GateAgent extends BaseNodeAgent {

    /** Gate 轮次上限：复判超过 3 轮仍不够 → AI 代选兜底 */
    private static final int GATE_MAX_ROUND = 3;

    private final LlmProvider llmProvider;
    private final SkillRegistry skillRegistry;
    private final ChatMemoryService memoryService;

    public GateAgent(AgentMemoryService agentMemory, LlmProvider llmProvider,
                     SkillRegistry skillRegistry, ChatMemoryService memoryService) {
        super(agentMemory);
        this.llmProvider = llmProvider;
        this.skillRegistry = skillRegistry;
        this.memoryService = memoryService;
    }

    @Override
    public String name() {
        return "gateAgent";
    }

    @Override
    public String systemPrompt() {
        return "信息充分性判断 Agent（HITL Gate）：按提示词与上下文判断能否直接生成；不够时给出缺口与用户可点选的候选方向"
                + "（出选项卡挂起，用户选择后回本节点复判）；够时列出生成前需要调用的技能。轮次封顶后 AI 代选兜底，保证收敛。";
    }

    @Override
    public void invoke(ChatFlowState state) {
        // 超时兜底恢复 / 用户已选「直接生成」：不再询问，AI 代选补齐后视为足够
        if (ChatFlowState.RESUME_TIMEOUT.equals(state.getResumeType()) || state.isDirectGenerate()) {
            aiDecideFallback(state);
            if (state.getNeedSkills().isEmpty()) {
                state.setNeedSkills(skillRegistry.filterValid(Arrays.asList("queryProduct", "queryCalendar")));
            }
            finishVerdict(state, true, new ArrayList<String>(), "选项卡超时或用户选择直接生成，AI 代选补齐");
            memoryService.clearPending(state.getSession().getId());
            return;
        }

        int round = loadRound(state);
        boolean ready;
        List<String> missing;
        List<String> options;
        List<String> skills;
        String reason;

        try {
            LlmResponse response = llmProvider.complete(LlmRequest.gate(state.getSession().getScene(),
                    state.getContextJson(), state.getHistoryJson(), state.getUserInput()));
            ready = Boolean.TRUE.equals(response.getReady());
            missing = safeList(response.getMissing());
            options = safeList(response.getOptions());
            skills = skillRegistry.filterValid(response.getNeedSkills());
            reason = response.getReason();
            // Gate 顺带抽取的槽位补丁（如用户选项卡选择里提到的商品方向）合并回上下文
            if (response.getContextPatchJson() != null && !response.getContextPatchJson().trim().isEmpty()) {
                state.setContextJson(mergeContext(state.getContextJson(), response.getContextPatchJson()));
            }
        } catch (RuntimeException exception) {
            // Gate 失败兜底：按代码缺口判断（必填槽位齐 → 足够），技能默认取商品资料
            ready = state.getMissingRequired().isEmpty();
            missing = new ArrayList<String>(state.getMissingRequired());
            options = new ArrayList<String>();
            skills = ready
                    ? skillRegistry.filterValid(Collections.singletonList("queryProduct"))
                    : new ArrayList<String>();
            reason = "gate 调用失败，按必填槽位兜底判断";
        }

        // 轮次封顶：复判仍不够 → AI 代选，保证收敛
        if (!ready && round + 1 >= GATE_MAX_ROUND) {
            aiDecideFallback(state);
            ready = true;
            missing = new ArrayList<String>();
            if (skills.isEmpty()) {
                skills = skillRegistry.filterValid(Collections.singletonList("queryProduct"));
            }
            reason = "选项卡复判轮次已达上限，AI 代选补齐";
        }

        state.setGateMissing(missing);
        state.setGateOptions(options);
        state.setNeedSkills(skills);
        state.setGateReason(reason);
        finishVerdict(state, ready, missing, reason);
        state.setGateRound(round + 1);
        remember(state, "round", String.valueOf(round + 1));
    }

    // ==================== 内部逻辑 ====================

    /** 判定结论落 state + 隔离记忆（本 Agent 的判断历史）。 */
    private void finishVerdict(ChatFlowState state, boolean ready, List<String> missing, String reason) {
        state.setSufficiency(ready ? ChatFlowState.SUFFICIENT_ENOUGH : ChatFlowState.SUFFICIENT_NOT);
        state.setGateMissing(missing);
        state.setGateReason(reason);
        try {
            Map<String, Object> verdict = new LinkedHashMap<String, Object>();
            verdict.put("round", recall(state, "round") == null ? 0 : recall(state, "round"));
            verdict.put("ready", ready);
            verdict.put("missing", missing);
            verdict.put("needSkills", state.getNeedSkills());
            verdict.put("reason", reason);
            remember(state, "lastVerdict", objectMapper.writeValueAsString(verdict));
        } catch (Exception ignored) {
            // 记忆写失败不影响主流程
        }
    }

    /** 判断轮次：隔离记忆优先，miss 回退 state（checkpoint 恢复场景）。 */
    private int loadRound(ChatFlowState state) {
        String stored = recall(state, "round");
        if (stored != null) {
            try {
                return Integer.parseInt(stored);
            } catch (NumberFormatException ignored) {
                // 记忆损坏回退 state
            }
        }
        return state.getGateRound();
    }

    /** AI 代选兜底（原 aiDecideSlots）：按默认值补齐必填缺口，成品标注「AI 代选，可改」。 */
    private void aiDecideFallback(ChatFlowState state) {
        for (String slotKey : state.getMissingRequired()) {
            String defaultValue;
            if ("product".equals(slotKey)) {
                defaultValue = "本店最近热销款";
            } else if ("platform".equals(slotKey)) {
                defaultValue = "朋友圈";
            } else {
                defaultValue = "AI 推荐";
            }
            state.setContextJson(mergeContext(state.getContextJson(), patch(slotKey, defaultValue)));
            state.getAiDecidedSlots().add(slotKey);
        }
        remember(state, "aiDecided", jsonList(state.getAiDecidedSlots()));
    }
}
