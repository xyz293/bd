package com.xiaoa.tenant.dto;

import javax.validation.constraints.NotBlank;

public class LoginRequest {

    @NotBlank(message = "微信标识不能为空")
    private String openid;

    public String getOpenid() {
        return openid;
    }

    public void setOpenid(String openid) {
        this.openid = openid;
    }
}
