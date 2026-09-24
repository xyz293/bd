package com.xiaoa.quota.dto;

import javax.validation.constraints.NotBlank;

public class PlatformLoginRequest {

    @NotBlank
    private String loginName;
    @NotBlank
    private String credential;

    public String getLoginName() { return loginName; }
    public void setLoginName(String loginName) { this.loginName = loginName; }
    public String getCredential() { return credential; }
    public void setCredential(String credential) { this.credential = credential; }
}
