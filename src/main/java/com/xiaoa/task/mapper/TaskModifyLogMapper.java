package com.xiaoa.task.mapper;

import com.xiaoa.task.model.TaskModifyLog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface TaskModifyLogMapper {

    @Insert("INSERT INTO task_modify_log (tenant_id, task_id, modified_by, change_detail) "
            + "VALUES (#{tenantId}, #{taskId}, #{modifiedBy}, #{changeDetail})")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(TaskModifyLog log);

    @Select("SELECT id, tenant_id, task_id, modified_by, change_detail, created_at "
            + "FROM task_modify_log WHERE tenant_id = #{tenantId} AND task_id = #{taskId} ORDER BY created_at DESC")
    List<TaskModifyLog> findByTask(@Param("tenantId") Long tenantId, @Param("taskId") Long taskId);
}
