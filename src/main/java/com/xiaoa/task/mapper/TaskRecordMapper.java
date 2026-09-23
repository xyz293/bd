package com.xiaoa.task.mapper;

import com.xiaoa.task.model.TaskRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface TaskRecordMapper {

    @Insert("INSERT INTO task_record (tenant_id, task_id, user_id, store_id, period_date, status) "
            + "VALUES (#{tenantId}, #{taskId}, #{userId}, #{storeId}, #{periodDate}, 0) "
            + "ON DUPLICATE KEY UPDATE id = id")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int ensure(TaskRecord record);

    @Select("SELECT id, tenant_id, task_id, user_id, store_id, period_date, status, publish_record_id, finished_at, created_at "
            + "FROM task_record WHERE tenant_id = #{tenantId} AND task_id = #{taskId} AND user_id = #{userId} "
            + "AND period_date = #{periodDate}")
    TaskRecord findOne(@Param("tenantId") Long tenantId, @Param("taskId") Long taskId,
                       @Param("userId") Long userId, @Param("periodDate") LocalDate periodDate);

    @Update("UPDATE task_record SET status = 1, publish_record_id = #{publishRecordId}, finished_at = #{finishedAt} "
            + "WHERE tenant_id = #{tenantId} AND task_id = #{taskId} AND user_id = #{userId} "
            + "AND period_date = #{periodDate} AND status = 0")
    int complete(@Param("tenantId") Long tenantId, @Param("taskId") Long taskId, @Param("userId") Long userId,
                 @Param("periodDate") LocalDate periodDate, @Param("publishRecordId") Long publishRecordId,
                 @Param("finishedAt") LocalDateTime finishedAt);

    @Select("SELECT id, tenant_id, task_id, user_id, store_id, period_date, status, publish_record_id, finished_at, created_at "
            + "FROM task_record WHERE tenant_id = #{tenantId} AND user_id = #{userId} AND period_date = #{periodDate} "
            + "ORDER BY status, task_id")
    List<TaskRecord> findByUserPeriod(@Param("tenantId") Long tenantId, @Param("userId") Long userId,
                                      @Param("periodDate") LocalDate periodDate);

    @Select("SELECT id, tenant_id, task_id, user_id, store_id, period_date, status, publish_record_id, finished_at, created_at "
            + "FROM task_record WHERE tenant_id = #{tenantId} AND store_id = #{storeId} AND period_date = #{periodDate} "
            + "AND status = #{status} ORDER BY user_id, task_id")
    List<TaskRecord> findByStorePeriodStatus(@Param("tenantId") Long tenantId, @Param("storeId") Long storeId,
                                             @Param("periodDate") LocalDate periodDate, @Param("status") Integer status);

    @Select("SELECT COUNT(*) FROM task_record WHERE tenant_id = #{tenantId} AND store_id = #{storeId} "
            + "AND period_date = #{periodDate} AND status = 1")
    long countFinished(@Param("tenantId") Long tenantId, @Param("storeId") Long storeId,
                       @Param("periodDate") LocalDate periodDate);

    @Select("SELECT COUNT(*) FROM task_record WHERE tenant_id = #{tenantId} AND store_id = #{storeId} "
            + "AND period_date = #{periodDate}")
    long countExpected(@Param("tenantId") Long tenantId, @Param("storeId") Long storeId,
                       @Param("periodDate") LocalDate periodDate);

    @Select("SELECT COUNT(*) FROM task_record WHERE tenant_id = #{tenantId} AND task_id = #{taskId} "
            + "AND store_id = #{storeId} AND period_date = #{periodDate} AND status = 0")
    long countUnfinishedByTaskStorePeriod(@Param("tenantId") Long tenantId, @Param("taskId") Long taskId,
                                          @Param("storeId") Long storeId, @Param("periodDate") LocalDate periodDate);
}
