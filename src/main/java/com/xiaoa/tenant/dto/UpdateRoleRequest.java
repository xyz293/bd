package com.xiaoa.tenant.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

public class UpdateRoleRequest {

    @NotNull(message = "组织不能为空")
    private Long orgId;

    @NotBlank(message = "角色不能为空")
    private String role;

    @NotNull(message = "数据范围不能为空")
    private Integer dataScope;

    public Long getOrgId() {
        return orgId;
    }

    public void setOrgId(Long orgId) {
        this.orgId = orgId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Integer getDataScope() {
        return dataScope;
    }

    public void setDataScope(Integer dataScope) {
        this.dataScope = dataScope;
    }
}
