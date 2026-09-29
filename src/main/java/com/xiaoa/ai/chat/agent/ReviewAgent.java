package com.xiaoa.ai.chat.agent;

import com.xiaoa.ai.chat.graph.ChatFlowState;
import com.xiaoa.ai.chat.provider.LlmProvider;
import com.xiaoa.ai.chat.provider.LlmRequest;
import com.xiaoa.ai.chat.provider.LlmResponse;
import com.xiaoa.admin.service.ComplianceService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 合规审查 Agent（reviewAndRespond 节点）：
 * 行业词库红线逐版过滤；不过 → 带合规意见重生成 1 次；仍不过 → 输出警示草稿
 * （预扣额度不退，员工需人工检查）。
 *
 * <p>隔离记忆：是否触发过重生成（regenerated）。</p>
 */
@Component
public class ReviewAgent extends BaseNodeAgent {

    private final ComplianceService complianceService;
    private final LlmProvider llmProvider;

    public ReviewAgent(AgentMemoryService agentMemory, ComplianceService complianceService,
                       LlmProvider llmProvider) {
        super(agentMemory);
        this.complianceService = complianceService;
        this.llmProvider = llmProvider;
    }

    @Override
    public String name() {
        return "reviewAgent";
    }

    @Override
    public String systemPrompt() {
        return "合规审查 Agent：行业词库红线逐版过滤（level1 替换、level2 拦截）；不过 → 带意见重生成 1 次；"
                + "仍不过 → 输出警示草稿（预扣额度不退）。";
    }

    @Override
    public void invoke(ChatFlowState state) {
        List<String> filtered = filterVersions(state);
        if (allBlank(filtered) && !state.isRegenerated()) {
            state.setRegenerated(true);
            String genContext = mergeContext(state.getContextJson(),
                    patch("complianceHint", "严格避免「最高级、投资价值、保值升值」等珠宝行业红线词，改用生活化表达"));
            List<String> regenerated = null;
            try {
                LlmResponse response = llmProvider.complete(LlmRequest.chat(state.getSession().getScene(),
                        genContext, state.getHistoryJson(), state.getUserInput()));
                regenerated = response.getVersions();
            } catch (RuntimeException exception) {
                regenerated = null;
            }
            if (regenerated == null || regenerated.isEmpty() || allBlank(regenerated)) {
                regenerated = fallbackVersions(state);
            }
            state.setVersions(regenerated);
            filtered = filterVersions(state);
        }
        if (allBlank(filtered)) {
            List<String> warning = new ArrayList<String>();
            warning.add("【警示草稿】该内容触发行业合规红线，请人工检查调整后再发布。");
            state.setVersions(warning);
        } else {
            state.setVersions(filtered);
        }
        state.setReplyAction(ChatFlowState.REPLY_GENERATE);
        remember(state, "regenerated", String.valueOf(state.isRegenerated()));
    }

    /** 逐版合规过滤（level1 替换、level2 拦截返回空）。 */
    private List<String> filterVersions(ChatFlowState state) {
        List<String> filtered = new ArrayList<String>();
        for (String version : state.getVersions()) {
            filtered.add(complianceService.filterText(state.tenantId(), version));
        }
        return filtered;
    }
}
