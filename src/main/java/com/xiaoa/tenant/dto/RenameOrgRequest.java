package com.xiaoa.tenant.dto;

import javax.validation.constraints.NotBlank;

public class RenameOrgRequest {

    @NotBlank(message = "组织名称不能为空")
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
