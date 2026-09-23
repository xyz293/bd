package com.xiaoa.ai.mapper;

import com.xiaoa.admin.model.StyleOption;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface StyleAiMapper {

    @Select("SELECT id, tenant_id, package_id, name, description, example_url, sort_no, status, version, created_at, updated_at "
            + "FROM style_option WHERE id = #{id} AND (tenant_id = #{tenantId} OR tenant_id IS NULL) AND status = 1")
    StyleOption findVisible(@Param("tenantId") Long tenantId, @Param("id") Long id);
}
