package com.xiaoa.asset.dto;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

public class ReviewAssetRequest {

    @NotNull(message = "审核结论不能为空")
    private Boolean pass;

    /** 通过时可指定品牌层分类，不传保持原分类 */
    @Size(max = 50, message = "分类长度不能超过50")
    private String category;

    public Boolean getPass() { return pass; }
    public void setPass(Boolean pass) { this.pass = pass; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
}
