package com.xiaoa.ai.chat.agent;

import com.xiaoa.ai.chat.graph.FactReport;
import com.xiaoa.ai.chat.graph.ChatFlowState;
import com.xiaoa.ai.chat.service.ChatBillingService;
import com.xiaoa.ai.chat.service.ChatMemoryService;
import com.xiaoa.common.exception.BusinessException;
import com.xiaoa.common.exception.ErrorCode;
import org.springframework.stereotype.Component;

/**
 * 核验与额度 Agent（verifyAndCharge / billingConfirm 节点）：
 * 事实核验 + 额度预扣（同一事务内顺序执行；核验不阻断，额度不足拦截）。
 *
 * <p>额度三态逻辑保持不变：预扣（hold）在同一事务内，任何异常整体回滚即自动释放；
 * confirm 为资金无操作（预扣即扣费）。</p>
 *
 * <p>隔离记忆：holdKey 与事实核验报告快照。</p>
 */
@Component
public class ChargeAgent extends BaseNodeAgent {

    private final ChatBillingService billingService;
    private final ChatMemoryService memoryService;

    public ChargeAgent(AgentMemoryService agentMemory, ChatBillingService billingService,
                       ChatMemoryService memoryService) {
        super(agentMemory);
        this.billingService = billingService;
        this.memoryService = memoryService;
    }

    @Override
    public String name() {
        return "chargeAgent";
    }

    @Override
    public String systemPrompt() {
        return "核验与额度 Agent：事实核验（槽位内字段视为已确认资料，缺失字段列 to_verify 强制占位符）"
                + "+ 额度预扣（hold）。额度三态不变：预扣即扣费，异常事务回滚自动释放，confirm 为语义确认。";
    }

    // ==================== verifyAndCharge 节点 ====================

    public void invoke(ChatFlowState state) {
        // checkpoint 恢复重入时清空旧报告，防确认事实重复累计
        state.setFactReport(new FactReport());
        verifyFacts(state);
        if (ChatFlowState.TASK_COPY.equals(state.getTaskType())) {
            state.setQuotaNeed(billingService.getCopyCost());
            String holdKey = "chat:" + state.getSession().getId() + ":hold:"
                    + memoryService.nextSeq(state.getSession().getId());
            try {
                billingService.checkAndHold(state.getPrincipal(), holdKey);
                state.setHoldKey(holdKey);
                state.setQuotaOk(true);
            } catch (BusinessException exception) {
                if (exception.getCode() == ErrorCode.QUOTA_NOT_ENOUGH.getCode()) {
                    state.setQuotaOk(false);
                } else {
                    throw exception;
                }
            }
        } else {
            // 图/视频由 AI 网关内部扣费（gen:{workId}）
            state.setQuotaOk(true);
        }
        // 隔离记忆：预扣键 + 事实报告快照
        remember(state, "holdKey", String.valueOf(state.getHoldKey()));
        remember(state, "quotaOk", String.valueOf(state.isQuotaOk()));
        rememberFactReport(state);
    }

    // ==================== billingConfirm 节点 ====================

    /** 预扣确认（confirm）：预扣即扣费（同一事务，异常自动回滚=释放），此处为语义确认。 */
    public void confirmHoldNode(ChatFlowState state) {
        if (state.getHoldKey() != null) {
            billingService.confirmHold();
            remember(state, "confirmed", "true");
        }
    }

    // ==================== 事实核验（不臆造） ====================

    /** 核验通过的进 confirmed，资料缺失的进 to_verify（生成时强制占位符）。 */
    private void verifyFacts(ChatFlowState state) {
        FactReport report = state.getFactReport();
        com.fasterxml.jackson.databind.JsonNode context = parse(state.getContextJson());
        boolean productAiDecided = state.getAiDecidedSlots().contains("product");
        String product = textOf(context, "product");
        report.addConfirmed("商品：" + (product == null ? "未指定" : product) + (productAiDecided ? "（AI 代选，可改）" : ""));
        String platform = defaultIfBlank(textOf(context, "platform"),
                defaultIfBlank(state.getSession().getScene(), "朋友圈"));
        report.addConfirmed("平台：" + platform);
        String festival = textOf(context, "festival");
        if (!isBlank(festival)) {
            report.addConfirmed("节日：" + festival);
        }
        // 商品资料缺失字段 → 占位符（发布前请补真实信息）
        if (isBlank(textOf(context, "price"))) {
            report.addTodoVerify("价格");
        }
        if (isBlank(textOf(context, "material"))) {
            report.addTodoVerify("材质");
        }
    }

    private void rememberFactReport(ChatFlowState state) {
        try {
            remember(state, "factReport", objectMapper.writeValueAsString(state.getFactReport()));
        } catch (Exception ignored) {
            // 记忆写失败不影响主流程
        }
    }
}
