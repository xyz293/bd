package com.xiaoa.quota.dto;

public class PlatformTokenResponse {

    private final String token;
    private final Long platformUserId;
    private final String role;

    public PlatformTokenResponse(String token, Long platformUserId, String role) {
        this.token = token;
        this.platformUserId = platformUserId;
        this.role = role;
    }

    public String getToken() { return token; }
    public Long getPlatformUserId() { return platformUserId; }
    public String getRole() { return role; }
}
