package com.xiaoa.tenant.mapper;

import com.xiaoa.tenant.model.UserOrgRole;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface UserOrgRoleMapper {

    @Insert("INSERT INTO user_org_role (tenant_id, user_id, org_id, role, data_scope, status) "
            + "VALUES (#{tenantId}, #{userId}, #{orgId}, #{role}, #{dataScope}, 1)")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(UserOrgRole role);

    @Select("SELECT id, tenant_id, user_id, org_id, role, data_scope, status, created_at "
            + "FROM user_org_role WHERE user_id = #{userId} AND status = 1 ORDER BY id")
    List<UserOrgRole> findActiveByUserId(Long userId);

    @Select("SELECT id, tenant_id, user_id, org_id, role, data_scope, status, created_at "
            + "FROM user_org_role WHERE tenant_id = #{tenantId} AND status = 1 ORDER BY user_id, id")
    List<UserOrgRole> findActiveByTenantId(Long tenantId);

    @Select("SELECT id, tenant_id, user_id, org_id, role, data_scope, status, created_at "
            + "FROM user_org_role WHERE id = #{id} AND tenant_id = #{tenantId}")
    UserOrgRole findById(@org.apache.ibatis.annotations.Param("id") Long id,
                         @org.apache.ibatis.annotations.Param("tenantId") Long tenantId);

    @Select("SELECT id, tenant_id, user_id, org_id, role, data_scope, status, created_at "
            + "FROM user_org_role WHERE tenant_id = #{tenantId} AND user_id = #{userId} "
            + "AND org_id = #{orgId} AND role = #{role} LIMIT 1")
    UserOrgRole findByUserOrgRole(@org.apache.ibatis.annotations.Param("tenantId") Long tenantId,
                                  @org.apache.ibatis.annotations.Param("userId") Long userId,
                                  @org.apache.ibatis.annotations.Param("orgId") Long orgId,
                                  @org.apache.ibatis.annotations.Param("role") String role);

    @Update("UPDATE user_org_role SET status = #{status} WHERE id = #{id} AND tenant_id = #{tenantId}")
    int updateStatus(@org.apache.ibatis.annotations.Param("id") Long id,
                     @org.apache.ibatis.annotations.Param("tenantId") Long tenantId,
                     @org.apache.ibatis.annotations.Param("status") Integer status);

    @Update("UPDATE user_org_role SET role = #{role}, data_scope = #{dataScope}, status = 1 "
            + "WHERE id = #{id} AND tenant_id = #{tenantId}")
    int updateRole(@org.apache.ibatis.annotations.Param("id") Long id,
                   @org.apache.ibatis.annotations.Param("tenantId") Long tenantId,
                   @org.apache.ibatis.annotations.Param("role") String role,
                   @org.apache.ibatis.annotations.Param("dataScope") Integer dataScope);
}
