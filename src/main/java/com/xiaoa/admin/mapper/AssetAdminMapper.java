package com.xiaoa.admin.mapper;

import com.xiaoa.admin.model.AssetAdminItem;
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
}
