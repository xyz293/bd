package com.xiaoa.quota.dto;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

public class CreditQuotaRequest {

    @NotNull
    private Long accountId;
    @NotNull @Min(1)
    private Long amount;
    private String bizId;
    private String remark;

    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }
    public Long getAmount() { return amount; }
    public void setAmount(Long amount) { this.amount = amount; }
    public String getBizId() { return bizId; }
    public void setBizId(String bizId) { this.bizId = bizId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
