package com.xiaoa.admin.dto;

import javax.validation.constraints.NotBlank;

public class CreateAssetRequest {

    private Long packageId;
    @NotBlank
    private String type;
    @NotBlank
    private String name;
    @NotBlank
    private String content;
    @NotBlank
    private String version;

    public Long getPackageId() { return packageId; }
    public void setPackageId(Long packageId) { this.packageId = packageId; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
}
