package com.xiaoa.tenant.dto;

import javax.validation.constraints.Future;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;

public class CreateInviteRequest {

    @NotNull(message = "门店不能为空")
    private Long storeId;

    @Future(message = "邀请码过期时间必须是未来时间")
    @NotNull(message = "过期时间不能为空")
    private LocalDateTime expireAt;

    private String role = "STAFF";

    public Long getStoreId() {
        return storeId;
    }

    public void setStoreId(Long storeId) {
        this.storeId = storeId;
    }

    public LocalDateTime getExpireAt() {
        return expireAt;
    }

    public void setExpireAt(LocalDateTime expireAt) {
        this.expireAt = expireAt;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}
