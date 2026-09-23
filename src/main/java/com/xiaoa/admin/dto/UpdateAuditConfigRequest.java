package com.xiaoa.admin.dto;

import javax.validation.constraints.NotNull;

public class UpdateAuditConfigRequest {

    @NotNull
    private Boolean enabled;

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
}
