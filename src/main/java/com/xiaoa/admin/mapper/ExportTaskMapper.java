package com.xiaoa.admin.mapper;

import com.xiaoa.admin.model.ExportTask;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ExportTaskMapper {

    @Insert("INSERT INTO export_task (tenant_id, created_by, export_type, query_params, status) "
            + "VALUES (#{tenantId}, #{createdBy}, #{exportType}, #{queryParams}, 0)")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(ExportTask exportTask);

    @Select("SELECT id, tenant_id, created_by, export_type, query_params, status, file_url, fail_reason, created_at, finished_at "
            + "FROM export_task WHERE tenant_id = #{tenantId} AND id = #{id}")
    ExportTask findById(@Param("tenantId") Long tenantId, @Param("id") Long id);

    @Select("SELECT id, tenant_id, created_by, export_type, query_params, status, file_url, fail_reason, created_at, finished_at "
            + "FROM export_task WHERE tenant_id = #{tenantId} ORDER BY created_at DESC")
    List<ExportTask> findByTenant(@Param("tenantId") Long tenantId);

    @Select("SELECT COUNT(*) FROM export_task WHERE tenant_id = #{tenantId} AND status = 0")
    long countRunning(@Param("tenantId") Long tenantId);

    @Update("UPDATE export_task SET status = #{status}, file_url = #{fileUrl}, fail_reason = #{failReason}, finished_at = NOW() "
            + "WHERE tenant_id = #{tenantId} AND id = #{id}")
    int finish(@Param("tenantId") Long tenantId, @Param("id") Long id, @Param("status") Integer status,
               @Param("fileUrl") String fileUrl, @Param("failReason") String failReason);
}
