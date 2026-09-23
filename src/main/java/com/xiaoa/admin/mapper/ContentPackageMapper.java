package com.xiaoa.admin.mapper;

import com.xiaoa.admin.model.ContentPackage;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ContentPackageMapper {

    @Insert("INSERT INTO content_package (tenant_id, name, calendar_date, publish_at, copy_direction, task_template, status, created_by) "
            + "VALUES (#{tenantId}, #{name}, #{calendarDate}, #{publishAt}, #{copyDirection}, #{taskTemplate}, 1, #{createdBy})")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(ContentPackage contentPackage);

    @Select("SELECT id, tenant_id, name, calendar_date, publish_at, copy_direction, task_template, status, created_by, created_at, updated_at "
            + "FROM content_package WHERE tenant_id = #{tenantId} ORDER BY calendar_date, id")
    List<ContentPackage> findAll(@Param("tenantId") Long tenantId);

    @Update("UPDATE content_package SET status = #{status} WHERE id = #{id} AND tenant_id = #{tenantId}")
    int updateStatus(@Param("tenantId") Long tenantId, @Param("id") Long id, @Param("status") Integer status);

    @Select("SELECT id, tenant_id, name, calendar_date, publish_at, copy_direction, task_template, status, created_by, created_at, updated_at "
            + "FROM content_package WHERE id = #{id} AND tenant_id = #{tenantId}")
    ContentPackage findById(@Param("tenantId") Long tenantId, @Param("id") Long id);

    @Select("SELECT id, tenant_id, name, calendar_date, publish_at, copy_direction, task_template, status, created_by, created_at, updated_at "
            + "FROM content_package WHERE status = 1 AND publish_at IS NOT NULL AND publish_at <= #{now}")
    List<ContentPackage> findDue(@Param("now") LocalDateTime now);
}
