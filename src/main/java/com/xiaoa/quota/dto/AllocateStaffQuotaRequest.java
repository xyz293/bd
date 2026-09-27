package com.xiaoa.quota.dto;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

/** 店长向员工划拨额度请求。memberRoleId 为 user_org_role.id。 */
public class AllocateStaffQuotaRequest {

    @NotNull(message = "成员不能为空")
    private Long memberRoleId;

    @NotNull(message = "划拨额度不能为空")
    @Min(value = 1, message = "划拨额度必须大于0")
    private Long amount;

    /** 幂等键，≤64 字符，重复提交不会重复划拨 */
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
