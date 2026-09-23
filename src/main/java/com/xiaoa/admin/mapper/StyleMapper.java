package com.xiaoa.admin.mapper;

import com.xiaoa.admin.model.StyleOption;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface StyleMapper {

    @Insert("INSERT INTO style_option (tenant_id, package_id, name, description, example_url, sort_no, status, version) "
            + "VALUES (#{tenantId}, #{packageId}, #{name}, #{description}, #{exampleUrl}, #{sortNo}, 1, 1)")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(StyleOption style);

    @Select("SELECT id, tenant_id, package_id, name, description, example_url, sort_no, status, version, created_at, updated_at "
            + "FROM style_option WHERE (tenant_id = #{tenantId} OR tenant_id IS NULL) AND status = 1 ORDER BY sort_no, id")
    List<StyleOption> findVisible(@Param("tenantId") Long tenantId);

    @Update("UPDATE style_option SET name = #{name}, description = #{description}, example_url = #{exampleUrl}, "
            + "sort_no = #{sortNo}, version = version + 1 WHERE id = #{id} AND tenant_id = #{tenantId}")
    int update(StyleOption style);

    @Delete("DELETE FROM style_option WHERE id = #{id} AND tenant_id = #{tenantId}")
    int delete(@Param("tenantId") Long tenantId, @Param("id") Long id);
}
