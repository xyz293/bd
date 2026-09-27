package com.xiaoa.tenant.model;

import java.time.LocalDateTime;

public class UserAccount {

    private Long id;
    private String phone;
    private String openid;
    private String nickname;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    /** 非表字段：成员额度余额（STAFF→员工账户，OWNER→门店账户，管理层→租户池），由服务层填充。 */
    private Long quotaBalance;
    /** 非表字段：STAFF 成员关系 ID（user_org_role.id），店长划拨/回收的 memberRoleId 取自这里。 */
    private Long userOrgRoleId;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getOpenid() {
        return openid;
    }

    public void setOpenid(String openid) {
        this.openid = openid;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Long getQuotaBalance() {
        return quotaBalance;
    }

    public void setQuotaBalance(Long quotaBalance) {
        this.quotaBalance = quotaBalance;
    }

    public Long getUserOrgRoleId() {
        return userOrgRoleId;
    }

    public void setUserOrgRoleId(Long userOrgRoleId) {
        this.userOrgRoleId = userOrgRoleId;
    }
}
