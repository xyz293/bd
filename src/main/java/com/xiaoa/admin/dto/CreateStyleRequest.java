package com.xiaoa.admin.dto;

import javax.validation.constraints.NotBlank;

public class CreateStyleRequest {

    @NotBlank
    private String name;
    private String description;
    private String exampleUrl;
    private Integer sortNo = 0;
    private Long packageId;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getExampleUrl() { return exampleUrl; }
    public void setExampleUrl(String exampleUrl) { this.exampleUrl = exampleUrl; }
    public Integer getSortNo() { return sortNo; }
    public void setSortNo(Integer sortNo) { this.sortNo = sortNo; }
    public Long getPackageId() { return packageId; }
    public void setPackageId(Long packageId) { this.packageId = packageId; }
}
