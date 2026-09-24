package com.xiaoa.asset.model;

import java.time.LocalDateTime;

/**
 * 素材。三层可见性：PLATFORM（行业包）/ BRAND（品牌）/ STORE（本店）。
 * 一期版本策略：素材不允许覆盖上传，"版本"只存在于 asset_package 层面，素材层面新增即新版本。
 */
public class Asset {

    public static final String SCOPE_PLATFORM = "PLATFORM";
    public static final String SCOPE_BRAND = "BRAND";
    public static final String SCOPE_STORE = "STORE";

    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_PENDING_REVIEW = "PENDING_REVIEW";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_DELETED = "DELETED";

    private Long id;
    private Long tenantId;
    private Long packageId;
    private Long storeId;
    private String scope;
    /** IMAGE / VIDEO / SCRIPT 等 */
    private String type;
    private String name;
    /** 文件地址或话术内容 */
    private String content;
    private String version;
    private String category;
    private String status;
    private Long uploaderId;
    /** 旧推优字段，仅保留历史数据兼容，新流程以 status + scope 为准。 */
    private Integer recommendStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getPackageId() { return packageId; }
    public void setPackageId(Long packageId) { this.packageId = packageId; }
    public Long getStoreId() { return storeId; }
    public void setStoreId(Long storeId) { this.storeId = storeId; }
    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getUploaderId() { return uploaderId; }
    public void setUploaderId(Long uploaderId) { this.uploaderId = uploaderId; }
    public Integer getRecommendStatus() { return recommendStatus; }
    public void setRecommendStatus(Integer recommendStatus) { this.recommendStatus = recommendStatus; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
