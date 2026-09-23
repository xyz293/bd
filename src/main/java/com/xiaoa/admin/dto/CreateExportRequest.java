package com.xiaoa.admin.dto;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

public class CreateExportRequest {

    @NotNull @Min(1) @Max(4)
    private Integer exportType;
    private String queryParams;

    public Integer getExportType() { return exportType; }
    public void setExportType(Integer exportType) { this.exportType = exportType; }
    public String getQueryParams() { return queryParams; }
    public void setQueryParams(String queryParams) { this.queryParams = queryParams; }
}
