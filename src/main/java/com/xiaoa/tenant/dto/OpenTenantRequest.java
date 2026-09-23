package com.xiaoa.tenant.dto;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import java.time.LocalDateTime;

public class OpenTenantRequest {

    @NotBlank(message = "租户名称不能为空")
    private String name;

    @NotNull(message = "租户类型不能为空")
    @Min(value = 1, message = "租户类型不正确")
    @Max(value = 2, message = "租户类型不正确")
    private Integer type;

    @NotBlank(message = "行业不能为空")
    private String industry;

    private Long assetPackageId;

    @NotBlank(message = "管理员手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String adminPhone;

    private String adminOpenid;
    private String adminNickname;
    private LocalDateTime expireAt;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getType() {
        return type;
    }

    public void setType(Integer type) {
        this.type = type;
    }

    public String getIndustry() {
        return industry;
    }

    public void setIndustry(String industry) {
        this.industry = industry;
    }

    public Long getAssetPackageId() {
        return assetPackageId;
    }

    public void setAssetPackageId(Long assetPackageId) {
        this.assetPackageId = assetPackageId;
    }

    public String getAdminPhone() {
        return adminPhone;
    }

    public void setAdminPhone(String adminPhone) {
        this.adminPhone = adminPhone;
    }

    public String getAdminOpenid() {
        return adminOpenid;
    }

    public void setAdminOpenid(String adminOpenid) {
        this.adminOpenid = adminOpenid;
    }

    public String getAdminNickname() {
        return adminNickname;
    }

    public void setAdminNickname(String adminNickname) {
        this.adminNickname = adminNickname;
    }

    public LocalDateTime getExpireAt() {
        return expireAt;
    }

    public void setExpireAt(LocalDateTime expireAt) {
        this.expireAt = expireAt;
    }
}
