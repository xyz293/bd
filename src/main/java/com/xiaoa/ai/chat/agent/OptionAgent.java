package com.xiaoa.ai.chat.agent;

import com.xiaoa.ai.chat.dto.OptionCardVO;
import com.xiaoa.ai.chat.dto.PendingOption;
import com.xiaoa.ai.chat.dto.QOptionVO;
import com.xiaoa.ai.chat.graph.ChatFlowState;
import com.xiaoa.ai.chat.service.ChatMemoryService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 选项卡 Agent（presentOptionCard / applyOption 节点）：
 * Gate 判定信息不足时，把缺口候选转成 A/B/C/D 选项卡挂起
 * （前端 30s 倒计时，后端 60s 兜底扫描，D 永远存在=直接生成）；
 * 恢复时把用户选择合并回上下文（clarification 累积），交回 Gate 复判。
 *
 * <p>隔离记忆：出过的选项（shownOptions）与用户最近选择（lastChoice）。</p>
 */
@Component
public class OptionAgent extends BaseNodeAgent {

    /** 选项卡前端倒计时秒数 */
    private static final int OPTION_CARD_DEADLINE_SECONDS = 30;

    private final ChatMemoryService memoryService;
    private final ContextLoader contextLoader;

    public OptionAgent(AgentMemoryService agentMemory, ChatMemoryService memoryService,
                       ContextLoader contextLoader) {
        super(agentMemory);
        this.memoryService = memoryService;
        this.contextLoader = contextLoader;
    }

    @Override
    public String name() {
        return "optionAgent";
    }

    @Override
    public String systemPrompt() {
        return "选项卡 Agent：Gate 判定信息不足时，把缺口候选转成 A/B/C/D 选项卡挂起（前端 30s 倒计时，后端 60s 兜底）；"
                + "恢复时把用户选择合并回上下文，交回 Gate 复判（不够再出卡，够则进技能+生成）。";
    }

    /** 默认入口 = 出卡挂起（图上按 present/applyChoice 两个节点引用）。 */
    @Override
    public void invoke(ChatFlowState state) {
        present(state);
    }

    // ==================== 出卡 + 挂起（presentOptionCard 节点） ====================

    public void present(ChatFlowState state) {
        List<QOptionVO> options = buildOptions(state);
        state.setOptionCard(new OptionCardVO(options, OPTION_CARD_DEADLINE_SECONDS));
        state.setReplyAction(ChatFlowState.REPLY_OPTION_CARD);
        state.setQuestion("还差一点信息（第 " + state.getGateRound() + " 轮）：选一个方向继续，或 30 秒后按当前信息生成～");
        holdPendingOption(state);
        // 隔离记忆：记录本轮出卡内容
        remember(state, "shownOptions", safeJson(options));
    }

    /** Gate 候选 → A/B/C + D（直接生成，永远存在）。 */
    private List<QOptionVO> buildOptions(ChatFlowState state) {
        List<String> candidates = new ArrayList<String>(state.getGateOptions());
        if (candidates.isEmpty()) {
            for (String gap : state.getGateMissing()) {
                candidates.add("补充：" + gap);
            }
        }
        if (candidates.isEmpty()) {
            candidates.add("我补充一下关键信息");
        }
        List<QOptionVO> options = new ArrayList<QOptionVO>();
        String[] keys = {"A", "B", "C"};
        for (int i = 0; i < Math.min(3, candidates.size()); i++) {
            options.add(new QOptionVO(keys[i], candidates.get(i), "点选后继续创作"));
        }
        options.add(new QOptionVO("D", "直接生成", "按当前信息执行，缺的由 AI 代选"));
        return options;
    }

    /** 挂起载荷：写恢复身份与截止时间（供 /chat/option 恢复与超时兜底扫描）。 */
    private void holdPendingOption(ChatFlowState state) {
        PendingOption pending = new PendingOption();
        pending.setTenantId(state.tenantId());
        pending.setUserId(state.getPrincipal().getUserId());
        pending.setOrgId(state.getPrincipal().getOrgId());
        pending.setRole(state.getPrincipal().getRole());
        pending.setDataScope(state.getPrincipal().getDataScope());
        pending.setSessionId(state.getSession().getId());
        pending.setDeadline(System.currentTimeMillis() + OPTION_CARD_DEADLINE_SECONDS * 1000L);
        memoryService.holdPendingOption(pending);
        memoryService.markPending(state.getSession().getId(), "OPTION_CARD");
        memoryService.track(state.tenantId(), "option_card_shown");
    }

    private String safeJson(List<QOptionVO> options) {
        try {
            return objectMapper.writeValueAsString(options);
        } catch (Exception exception) {
            return "[]";
        }
    }

    // ==================== 用户选择恢复（applyOption 节点） ====================

    /**
     * 应用用户选择后回 Gate 复判（循环）：
     * A/B/C → 候选文案并入 clarification 累积字段；D → 授权 AI 代选（directGenerate），Gate 直接放行。
     */
    public void applyChoice(ChatFlowState state) {
        contextLoader.load(state);
        String key = state.getOptionKey();
        String label = resolveLabel(state, key);
        if ("D".equals(key)) {
            // 用户选「直接生成」：授权 AI 代选补齐，下轮 Gate 不再询问
            state.setDirectGenerate(true);
        } else if (label != null) {
            String current = textOf(parse(state.getContextJson()), "clarification");
            String merged = isBlank(current) ? label : current + "；" + label;
            state.setContextJson(mergeContext(state.getContextJson(), patch("clarification", merged)));
        }
        memoryService.saveSlots(state.getSession().getId(), state.getContextJson());
        memoryService.clearPending(state.getSession().getId());
        memoryService.track(state.tenantId(), "option_card_" + (key == null ? "D" : key));
        // 隔离记忆：用户最近选择
        remember(state, "lastChoice", key + ":" + String.valueOf(label));
    }

    /** 从 checkpoint 恢复的选项卡里反查用户所选项的文案。 */
    private String resolveLabel(ChatFlowState state, String key) {
        if (state.getOptionCard() == null || state.getOptionCard().getOptions() == null || key == null) {
            return null;
        }
        for (QOptionVO option : state.getOptionCard().getOptions()) {
            if (option != null && key.equals(option.getKey())) {
                return option.getLabel();
            }
        }
        return null;
    }
}
