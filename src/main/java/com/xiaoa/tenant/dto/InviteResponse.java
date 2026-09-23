package com.xiaoa.tenant.dto;

import java.time.LocalDateTime;

public class InviteResponse {

    private final Long id;
    private final Long storeId;
    private final String code;
    private final String role;
    private final LocalDateTime expireAt;

    public InviteResponse(Long id, Long storeId, String code, String role, LocalDateTime expireAt) {
        this.id = id;
        this.storeId = storeId;
        this.code = code;
        this.role = role;
        this.expireAt = expireAt;
    }

    public Long getId() {
        return id;
    }

    public Long getStoreId() {
        return storeId;
    }

    public String getCode() {
        return code;
    }

    public String getRole() {
        return role;
    }

    public LocalDateTime getExpireAt() {
        return expireAt;
    }
}
