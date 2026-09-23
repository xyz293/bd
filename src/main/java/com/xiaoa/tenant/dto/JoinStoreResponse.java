package com.xiaoa.tenant.dto;

public class JoinStoreResponse {

    private final TokenResponse login;
    private final Long storeId;

    public JoinStoreResponse(TokenResponse login, Long storeId) {
        this.login = login;
        this.storeId = storeId;
    }

    public TokenResponse getLogin() {
        return login;
    }

    public Long getStoreId() {
        return storeId;
    }
}
