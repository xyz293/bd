package com.xiaoa.tenant.mapper;

import com.xiaoa.tenant.model.Org;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface OrgMapper {

    @Insert("INSERT INTO `org` (tenant_id, parent_id, type, name, deleted) "
            + "VALUES (#{tenantId}, #{parentId}, #{type}, #{name}, 0)")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(Org org);

    @Select("SELECT id, tenant_id, parent_id, type, name, created_at, updated_at "
            + "FROM `org` WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted = 0")
    Org findById(@org.apache.ibatis.annotations.Param("id") Long id,
                 @org.apache.ibatis.annotations.Param("tenantId") Long tenantId);

    @Select("SELECT id, tenant_id, parent_id, type, name, created_at, updated_at "
            + "FROM `org` WHERE tenant_id = #{tenantId} AND deleted = 0 ORDER BY type, id")
    List<Org> findByTenantId(Long tenantId);

    @Update("UPDATE `org` SET name = #{name} WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted = 0")
    int updateName(@org.apache.ibatis.annotations.Param("id") Long id,
                   @org.apache.ibatis.annotations.Param("tenantId") Long tenantId,
                   @org.apache.ibatis.annotations.Param("name") String name);
}
