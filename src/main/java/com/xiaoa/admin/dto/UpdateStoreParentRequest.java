package com.xiaoa.admin.dto;

import javax.validation.constraints.NotNull;

public class UpdateStoreParentRequest {

    @NotNull
    private Long parentId;

    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
}
