package com.xiaoa.quota.dto;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

/** 店长回收员工未用额度请求。回收金额不能超过员工当前余额。 */
public class RecallStaffQuotaRequest {

    @NotNull(message = "成员不能为空")
    private Long memberRoleId;

    @NotNull(message = "回收额度不能为空")
    @Min(value = 1, message = "回收额度必须大于0")
    private Long amount;

    private String bizId;
    private String remark;

    public Long getMemberRoleId() { return memberRoleId; }
    public void setMemberRoleId(Long memberRoleId) { this.memberRoleId = memberRoleId; }
    public Long getAmount() { return amount; }
    public void setAmount(Long amount) { this.amount = amount; }
    public String getBizId() { return bizId; }
    public void setBizId(String bizId) { this.bizId = bizId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
