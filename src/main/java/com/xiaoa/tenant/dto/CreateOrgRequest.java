package com.xiaoa.tenant.dto;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

public class CreateOrgRequest {

    @NotNull(message = "组织类型不能为空")
    @Min(value = 1, message = "组织类型不正确")
    @Max(value = 3, message = "组织类型不正确")
    private Integer type;

    @NotBlank(message = "组织名称不能为空")
    private String name;

    private Long parentId;

    public Integer getType() {
        return type;
    }

    public void setType(Integer type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }
}
