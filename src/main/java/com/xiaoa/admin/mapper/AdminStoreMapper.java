package com.xiaoa.admin.mapper;

import com.xiaoa.tenant.model.Org;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AdminStoreMapper {

    @Select("SELECT id, tenant_id, parent_id, type, name, created_at, updated_at FROM `org` "
            + "WHERE tenant_id = #{tenantId} AND type = 3 AND deleted = 0 ORDER BY id")
    List<Org> findStores(@Param("tenantId") Long tenantId);

    @Select("SELECT COUNT(*) FROM user_org_role WHERE tenant_id = #{tenantId} AND org_id = #{orgId} "
            + "AND status = 1")
    long countMembers(@Param("tenantId") Long tenantId, @Param("orgId") Long orgId);

    @Select("SELECT user_id FROM user_org_role WHERE tenant_id = #{tenantId} AND org_id = #{orgId} "
            + "AND role = 'OWNER' AND status = 1 ORDER BY id LIMIT 1")
    Long findOwner(@Param("tenantId") Long tenantId, @Param("orgId") Long orgId);

    @Select("SELECT id, tenant_id, parent_id, type, name, created_at, updated_at FROM `org` "
            + "WHERE id = #{orgId} AND tenant_id = #{tenantId} AND type = 3 AND deleted = 0")
    Org findStore(@Param("tenantId") Long tenantId, @Param("orgId") Long orgId);

    @Select("SELECT COUNT(*) FROM `org` WHERE tenant_id = #{tenantId} AND type = 3 AND deleted = 0")
    long countStores(@Param("tenantId") Long tenantId);
}
