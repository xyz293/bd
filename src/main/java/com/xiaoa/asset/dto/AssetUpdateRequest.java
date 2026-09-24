package com.xiaoa.asset.dto;

import javax.validation.constraints.Size;

public class AssetUpdateRequest {

    @Size(max = 128, message = "素材名称长度不能超过128")
    private String name;

    @Size(max = 50, message = "分类长度不能超过50")
    private String category;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
}
