package com.xiaoa.task.mapper;

import com.xiaoa.task.model.Task;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface TaskMapper {

    @Insert("INSERT INTO `task` (tenant_id, title, form_type, content_package_id, platform, frequency, "
            + "target_scope, target_ids, judge_type, source_task_id, created_by, created_level, status, start_at, end_at, deleted) "
            + "VALUES (#{tenantId}, #{title}, #{formType}, #{contentPackageId}, #{platform}, #{frequency}, "
            + "#{targetScope}, #{targetIds}, #{judgeType}, #{sourceTaskId}, #{createdBy}, #{createdLevel}, 1, #{startAt}, #{endAt}, 0)")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(Task task);

    @Select("SELECT id, tenant_id, title, form_type, content_package_id, platform, frequency, target_scope, "
            + "target_ids, judge_type, source_task_id, created_by, created_level, status, start_at, end_at, created_at, updated_at "
            + "FROM `task` WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted = 0")
    Task findById(@Param("id") Long id, @Param("tenantId") Long tenantId);

    @Select("SELECT id, tenant_id, title, form_type, content_package_id, platform, frequency, target_scope, "
            + "target_ids, judge_type, source_task_id, created_by, created_level, status, start_at, end_at, created_at, updated_at "
            + "FROM `task` WHERE tenant_id = #{tenantId} AND status = 1 AND deleted = 0 "
            + "AND (start_at IS NULL OR start_at <= #{now}) AND (end_at IS NULL OR end_at >= #{now}) "
            + "ORDER BY created_at DESC")
    List<Task> findActive(@Param("tenantId") Long tenantId, @Param("now") LocalDateTime now);

    @Select("SELECT id, tenant_id, title, form_type, content_package_id, platform, frequency, target_scope, "
            + "target_ids, judge_type, source_task_id, created_by, created_level, status, start_at, end_at, created_at, updated_at "
            + "FROM `task` WHERE tenant_id = #{tenantId} ORDER BY created_at DESC")
    List<Task> findAll(@Param("tenantId") Long tenantId);

    @Update("UPDATE `task` SET title = #{title}, content_package_id = #{contentPackageId}, platform = #{platform}, "
            + "start_at = #{startAt}, end_at = #{endAt} WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted = 0")
    int updateEditable(@Param("id") Long id, @Param("tenantId") Long tenantId, @Param("title") String title,
                       @Param("contentPackageId") Long contentPackageId, @Param("platform") String platform,
                       @Param("startAt") LocalDateTime startAt, @Param("endAt") LocalDateTime endAt);

    @Update("UPDATE `task` SET status = #{status} WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted = 0")
    int updateStatus(@Param("id") Long id, @Param("tenantId") Long tenantId, @Param("status") Integer status);

    @Select("SELECT id, tenant_id, title, form_type, content_package_id, platform, frequency, target_scope, "
            + "target_ids, judge_type, source_task_id, created_by, created_level, status, start_at, end_at, created_at, updated_at "
            + "FROM `task` WHERE tenant_id = #{tenantId} AND source_task_id = #{sourceTaskId} "
            + "AND JSON_CONTAINS(target_ids, CAST(#{storeId} AS JSON)) AND deleted = 0 LIMIT 1")
    Task findStoreVariant(@Param("tenantId") Long tenantId, @Param("sourceTaskId") Long sourceTaskId,
                          @Param("storeId") Long storeId);
}
