package com.xiaoa.admin.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
@Mapper
public interface AuditConfigMapper {

    @Select("SELECT enabled FROM audit_config WHERE tenant_id = #{tenantId} AND org_id = #{orgId}")
    Integer findEnabled(@Param("tenantId") Long tenantId, @Param("orgId") Long orgId);

    @Insert("INSERT INTO audit_config (tenant_id, org_id, enabled, updated_by) VALUES (#{tenantId}, #{orgId}, #{enabled}, #{updatedBy}) "
            + "ON DUPLICATE KEY UPDATE enabled = VALUES(enabled), updated_by = VALUES(updated_by), updated_at = CURRENT_TIMESTAMP")
    int upsert(@Param("tenantId") Long tenantId, @Param("orgId") Long orgId, @Param("enabled") Integer enabled,
               @Param("updatedBy") Long updatedBy);
}
