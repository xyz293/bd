package com.xiaoa.admin.mapper;

import com.xiaoa.admin.model.AssetAdminItem;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface AssetAdminMapper {

    @Select("SELECT id, tenant_id, package_id, type, name, content, version, status, owner_type, owner_id, "
            + "recommend_status, created_at, updated_at FROM asset WHERE tenant_id = #{tenantId} OR owner_type = 2 "
            + "ORDER BY updated_at DESC")
    List<AssetAdminItem> findVisible(@Param("tenantId") Long tenantId);

    @Update("UPDATE asset SET recommend_status = #{recommendStatus}, owner_type = CASE WHEN #{recommendStatus} = 2 THEN 2 ELSE owner_type END "
            + "WHERE id = #{id} AND (tenant_id = #{tenantId} OR owner_type = 2)")
    int review(@Param("tenantId") Long tenantId, @Param("id") Long id, @Param("recommendStatus") Integer recommendStatus);

    /**
     * 员工可见素材：平台挂载包/品牌推优（owner_type=2）或本租户自有素材；需 ACTIVE。没有独立门店级素材，门店复用本租户素材。
     */
    @Select("SELECT id, tenant_id, package_id, type, name, content, version, status, owner_type, owner_id, "
            + "recommend_status, created_at, updated_at FROM asset "
            + "WHERE id = #{id} AND status = 'ACTIVE' AND (tenant_id = #{tenantId} OR owner_type = 2)")
    AssetAdminItem findVisibleById(@Param("tenantId") Long tenantId, @Param("id") Long id);

    /**
     * 推优：置待审并记录推荐人；已在推优流程中（1 待审/2 已通过）的素材不允许重复提交。
     */
    @Update("UPDATE asset SET recommend_status = 1, owner_id = #{userId} "
            + "WHERE id = #{id} AND (tenant_id = #{tenantId} OR owner_type = 2) "
            + "AND recommend_status NOT IN (1, 2)")
    int markRecommended(@Param("tenantId") Long tenantId, @Param("id") Long id, @Param("userId") Long userId);
}
