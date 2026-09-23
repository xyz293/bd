package com.xiaoa.admin.mapper;

import com.xiaoa.admin.model.ComplianceWord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ComplianceWordMapper {

    @Insert("INSERT INTO compliance_word (tenant_id, word, level, replacement, status, created_by) "
            + "VALUES (#{tenantId}, #{word}, #{level}, #{replacement}, 1, #{createdBy})")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(ComplianceWord word);

    @Select("SELECT id, tenant_id, word, level, replacement, status, created_by, created_at, updated_at "
            + "FROM compliance_word WHERE tenant_id = #{tenantId} AND status = 1 ORDER BY level, id")
    List<ComplianceWord> findActive(@Param("tenantId") Long tenantId);

    @Update("UPDATE compliance_word SET word = #{word}, level = #{level}, replacement = #{replacement} "
            + "WHERE id = #{id} AND tenant_id = #{tenantId}")
    int update(ComplianceWord word);

    @Update("UPDATE compliance_word SET status = 2 WHERE id = #{id} AND tenant_id = #{tenantId}")
    int disable(@Param("tenantId") Long tenantId, @Param("id") Long id);

    @Select("SELECT id, tenant_id, word, level, replacement, status, created_by, created_at, updated_at "
            + "FROM compliance_word WHERE id = #{id} AND tenant_id = #{tenantId}")
    ComplianceWord findById(@Param("tenantId") Long tenantId, @Param("id") Long id);
}
