package com.xiaoa.asset.mapper;

import com.xiaoa.asset.model.Asset;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface AssetMapper {

    String COLUMNS = "id, tenant_id, package_id, store_id, scope, type, name, content, version, category, "
            + "status, uploader_id, recommend_status, created_at, updated_at";

    @Insert("INSERT INTO asset (tenant_id, package_id, store_id, scope, type, name, content, version, "
            + "category, status, uploader_id) "
            + "VALUES (#{tenantId}, #{packageId}, #{storeId}, #{scope}, #{type}, #{name}, #{content}, "
            + "#{version}, #{category}, #{status}, #{uploaderId})")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(Asset asset);

    @Select("SELECT " + COLUMNS + " FROM asset WHERE id = #{id} AND tenant_id = #{tenantId}")
    Asset findById(@Param("tenantId") Long tenantId, @Param("id") Long id);

    /**
     * 三层可见性即下发：品牌层（本租户）∪ 本店层 ∪ 平台层（租户挂载包/同行业包），仅 APPROVED。
     * storeId 为空（总部/区域角色）时本店层自然不命中。
     */
    @Select("<script>SELECT " + COLUMNS + " FROM asset WHERE status = 'APPROVED' AND ("
            + "(scope = 'BRAND' AND tenant_id = #{tenantId}) "
            + "OR (scope = 'STORE' AND store_id = #{storeId}) "
            + "OR (scope = 'PLATFORM' AND package_id IN ("
            + "SELECT ap.id FROM asset_package ap JOIN tenant t ON t.id = #{tenantId} "
            + "WHERE ap.id = t.asset_package_id OR ap.industry = t.industry))"
            + ") <if test=\"category != null and category != ''\">AND category = #{category}</if> "
            + "ORDER BY created_at DESC LIMIT #{limit}</script>")
    List<Asset> selectVisible(@Param("tenantId") Long tenantId, @Param("storeId") Long storeId,
                              @Param("category") String category, @Param("limit") int limit);

    /**
     * 单个素材的三层可见性判断，供推优与创作选素材复用。
     */
    @Select("SELECT " + COLUMNS + " FROM asset WHERE id = #{assetId} AND status = 'APPROVED' AND ("
            + "(scope = 'BRAND' AND tenant_id = #{tenantId}) "
            + "OR (scope = 'STORE' AND store_id = #{storeId}) "
            + "OR (scope = 'PLATFORM' AND package_id IN ("
            + "SELECT ap.id FROM asset_package ap JOIN tenant t ON t.id = #{tenantId} "
            + "WHERE ap.id = t.asset_package_id OR ap.industry = t.industry))"
            + ")")
    Asset findVisibleById(@Param("tenantId") Long tenantId, @Param("storeId") Long storeId,
                          @Param("assetId") Long assetId);

    @Update("UPDATE asset SET name = COALESCE(#{name}, name), category = COALESCE(#{category}, category) "
            + "WHERE id = #{id} AND tenant_id = #{tenantId} AND scope &lt;&gt; 'PLATFORM'")
    int updateNameCategory(@Param("tenantId") Long tenantId, @Param("id") Long id,
                           @Param("name") String name, @Param("category") String category);

    @Update("UPDATE asset SET status = #{status} "
            + "WHERE id = #{id} AND tenant_id = #{tenantId} AND scope &lt;&gt; 'PLATFORM'")
    int updateStatus(@Param("tenantId") Long tenantId, @Param("id") Long id, @Param("status") String status);

    /**
     * 推优审核通过：升层为品牌素材（同row升级），条件更新防重复审核。
     */
    @Update("UPDATE asset SET scope = 'BRAND', status = 'APPROVED', "
            + "category = COALESCE(#{category}, category), recommend_status = 2, owner_type = 2 "
            + "WHERE id = #{id} AND tenant_id = #{tenantId} AND scope = 'STORE' AND status = 'PENDING_REVIEW'")
    int promote(@Param("tenantId") Long tenantId, @Param("id") Long id, @Param("category") String category);

    /**
     * 推优提交：仅本店素材可推优，已在推优流程中的拒绝；条件更新防重复提交。
     */
    @Update("UPDATE asset SET status = 'PENDING_REVIEW' "
            + "WHERE id = #{id} AND tenant_id = #{tenantId} AND scope = 'STORE' "
            + "AND status NOT IN ('PENDING_REVIEW', 'APPROVED')")
    int markPendingReview(@Param("tenantId") Long tenantId, @Param("id") Long id);

    @Select("<script>SELECT " + COLUMNS + " FROM asset "
            + "WHERE (tenant_id = #{tenantId} OR scope = 'PLATFORM') "
            + "<if test=\"scope != null and scope != ''\">AND scope = #{scope}</if> "
            + "<if test=\"status != null and status != ''\">AND status = #{status}</if> "
            + "<if test=\"category != null and category != ''\">AND category = #{category}</if> "
            + "ORDER BY updated_at DESC LIMIT #{offset}, #{size}</script>")
    List<Asset> selectPage(@Param("tenantId") Long tenantId, @Param("scope") String scope,
                           @Param("status") String status, @Param("category") String category,
                           @Param("offset") int offset, @Param("size") int size);

    @Select("<script>SELECT COUNT(*) FROM asset "
            + "WHERE (tenant_id = #{tenantId} OR scope = 'PLATFORM') "
            + "<if test=\"scope != null and scope != ''\">AND scope = #{scope}</if> "
            + "<if test=\"status != null and status != ''\">AND status = #{status}</if> "
            + "<if test=\"category != null and category != ''\">AND category = #{category}</if> "
            + "</script>")
    long countPage(@Param("tenantId") Long tenantId, @Param("scope") String scope,
                   @Param("status") String status, @Param("category") String category);
}
