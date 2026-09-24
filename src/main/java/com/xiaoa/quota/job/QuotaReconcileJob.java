package com.xiaoa.quota.job;

import com.xiaoa.quota.mapper.PaymentOrderMapper;
import com.xiaoa.quota.mapper.QuotaAccountMapper;
import com.xiaoa.quota.mapper.QuotaFlowMapper;
import com.xiaoa.quota.mapper.QuotaReconcileMapper;
import com.xiaoa.quota.mapper.MediaTaskReconcileMapper;
import com.xiaoa.quota.model.QuotaAccount;
import com.xiaoa.quota.model.QuotaReconcileAlert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class QuotaReconcileJob {

    private static final Logger log = LoggerFactory.getLogger(QuotaReconcileJob.class);

    private final QuotaAccountMapper accountMapper;
    private final QuotaFlowMapper flowMapper;
    private final PaymentOrderMapper paymentOrderMapper;
    private final MediaTaskReconcileMapper mediaTaskMapper;
    private final QuotaReconcileMapper reconcileMapper;

    public QuotaReconcileJob(QuotaAccountMapper accountMapper, QuotaFlowMapper flowMapper,
                             PaymentOrderMapper paymentOrderMapper,
                             MediaTaskReconcileMapper mediaTaskMapper,
                             QuotaReconcileMapper reconcileMapper) {
        this.accountMapper = accountMapper;
        this.flowMapper = flowMapper;
        this.paymentOrderMapper = paymentOrderMapper;
        this.mediaTaskMapper = mediaTaskMapper;
        this.reconcileMapper = reconcileMapper;
    }

    @Scheduled(cron = "${xiaoa.quota.reconcile-cron:0 0 2 * * ?}")
    public void reconcile() {
        int alerts = 0;
        for (QuotaAccount account : accountMapper.findAll()) {
            Long latestBalance = flowMapper.findLatestBalanceAfter(account.getId());
            if (latestBalance != null && !latestBalance.equals(account.getBalance())) {
                insertAlert(account.getTenantId(), "ACCOUNT_LATEST_FLOW", String.valueOf(latestBalance),
                        String.valueOf(account.getBalance()), "账户余额与最新流水余额不一致");
                alerts++;
            }
        }

        for (Long tenantId : reconcileMapper.findTenantIds()) {
            long accountBalance = accountMapper.sumBalance(tenantId);
            long replayBalance = flowMapper.sumAmount(tenantId);
            if (accountBalance != replayBalance) {
                insertAlert(tenantId, "ACCOUNT_REPLAY", String.valueOf(replayBalance),
                        String.valueOf(accountBalance), "账户余额与全量流水重放结果不一致");
                alerts++;
            }

            long settledAmount = paymentOrderMapper.sumSettledAmount(tenantId);
            long creditAmount = flowMapper.sumCredit(tenantId);
            if (settledAmount != creditAmount) {
                insertAlert(tenantId, "PAYMENT_CREDIT", String.valueOf(settledAmount),
                        String.valueOf(creditAmount), "已结算收款单金额与 CREDIT 流水金额不一致");
                alerts++;
            }

            long refundedTasks = mediaTaskMapper.countFailedRefunded(tenantId);
            long refundFlows = flowMapper.countRefund(tenantId);
            if (refundedTasks != refundFlows) {
                insertAlert(tenantId, "MEDIA_REFUND", String.valueOf(refundedTasks),
                        String.valueOf(refundFlows), "失败已退款任务数与 REFUND 流水数不一致");
                alerts++;
            }
        }
        log.info("Quota reconciliation finished, alerts={}", alerts);
    }

    private void insertAlert(Long tenantId, String checkType, String expected, String actual, String detail) {
        QuotaReconcileAlert alert = new QuotaReconcileAlert();
        alert.setTenantId(tenantId);
        alert.setCheckType(checkType);
        alert.setExpectedValue(expected);
        alert.setActualValue(actual);
        alert.setDetail(detail);
        reconcileMapper.insertAlert(alert);
    }
}
