package com.xiaoa.asset.mapper;

import com.xiaoa.asset.model.ContentPackage;
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

    String COLUMNS = "id, tenant_id, name, calendar_date, publish_at, copy_direction, task_template, "
            + "status, source_task_id, last_error, created_by, created_at, updated_at";

    @Insert("INSERT INTO content_package (tenant_id, name, calendar_date, publish_at, copy_direction, "
            + "task_template, status, created_by) "
            + "VALUES (#{tenantId}, #{name}, #{calendarDate}, #{publishAt}, #{copyDirection}, "
            + "#{taskTemplate}, #{status}, #{createdBy})")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(ContentPackage contentPackage);

    @Select("SELECT " + COLUMNS + " FROM content_package WHERE id = #{id} AND tenant_id = #{tenantId}")
    ContentPackage findById(@Param("tenantId") Long tenantId, @Param("id") Long id);

    /**
     * 补发策略：查所有 publish_time 已过且仍 ACTIVE 的（不只当前分钟），服务重启错过窗口也能补发。
     */
    @Select("SELECT " + COLUMNS + " FROM content_package "
            + "WHERE status = 1 AND publish_at &lt;= #{now} ORDER BY publish_at LIMIT #{limit}")
    List<ContentPackage> selectDue(@Param("now") LocalDateTime now, @Param("limit") int limit);

    /**
     * 幂等核心：条件更新抢占，多实例并发只有一个抢到，抢到者才允许建任务。
     */
    @Update("UPDATE content_package SET status = 2 WHERE id = #{id} AND status = 1")
    int tryExpire(@Param("id") Long id);

    @Update("UPDATE content_package SET status = 3 WHERE id = #{id} AND tenant_id = #{tenantId} AND status = 1")
    int cancel(@Param("tenantId") Long tenantId, @Param("id") Long id);

    @Update("UPDATE content_package SET source_task_id = #{taskId} WHERE id = #{id}")
    int updateSourceTask(@Param("id") Long id, @Param("taskId") Long taskId);

    @Update("UPDATE content_package SET last_error = #{error} WHERE id = #{id}")
    int markError(@Param("id") Long id, @Param("error") String error);

    @Select("<script>SELECT " + COLUMNS + " FROM content_package WHERE tenant_id = #{tenantId} "
            + "<if test=\"status != null\">AND status = #{status}</if> "
            + "ORDER BY id DESC LIMIT #{offset}, #{size}</script>")
    List<ContentPackage> selectPage(@Param("tenantId") Long tenantId, @Param("status") Integer status,
                                    @Param("offset") int offset, @Param("size") int size);

    @Select("<script>SELECT COUNT(*) FROM content_package WHERE tenant_id = #{tenantId} "
            + "<if test=\"status != null\">AND status = #{status}</if> "
            + "</script>")
    long countPage(@Param("tenantId") Long tenantId, @Param("status") Integer status);
}
