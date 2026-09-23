package com.xiaoa.ai.mapper;

import com.xiaoa.ai.model.MediaTask;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface MediaTaskMapper {

    @Insert("INSERT INTO media_task (tenant_id, user_id, work_id, store_id, type, scene, status, prompt, cost, refund_status) "
            + "VALUES (#{tenantId}, #{userId}, #{workId}, #{storeId}, #{type}, #{scene}, 'PENDING', #{prompt}, #{cost}, 0)")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(MediaTask task);

    @Select("SELECT id, tenant_id, user_id, work_id, store_id, type, scene, status, prompt, provider_task_id, result_url, cost, "
            + "error_message, refund_status, started_at, finished_at, created_at, updated_at FROM media_task "
            + "WHERE id = #{id}")
    MediaTask findById(@Param("id") Long id);

    @Select("SELECT id, tenant_id, user_id, work_id, store_id, type, scene, status, prompt, provider_task_id, result_url, cost, "
            + "error_message, refund_status, started_at, finished_at, created_at, updated_at FROM media_task "
            + "WHERE status = 'PENDING' ORDER BY created_at LIMIT #{limit}")
    List<MediaTask> findPending(@Param("limit") int limit);

    @Select("SELECT id, tenant_id, user_id, work_id, store_id, type, scene, status, prompt, provider_task_id, result_url, cost, "
            + "error_message, refund_status, started_at, finished_at, created_at, updated_at FROM media_task "
            + "WHERE type = 'VIDEO' AND status = 'SUBMITTED' ORDER BY updated_at LIMIT #{limit}")
    List<MediaTask> findSubmittedVideos(@Param("limit") int limit);

    @Select("SELECT id, tenant_id, user_id, work_id, store_id, type, scene, status, prompt, provider_task_id, result_url, cost, "
            + "error_message, refund_status, started_at, finished_at, created_at, updated_at FROM media_task "
            + "WHERE status IN ('PENDING', 'PROCESSING', 'SUBMITTED', 'POLLING') "
            + "AND created_at < #{deadline} ORDER BY created_at LIMIT #{limit}")
    List<MediaTask> findTimeouts(@Param("deadline") LocalDateTime deadline, @Param("limit") int limit);

    @Select("SELECT id, tenant_id, user_id, work_id, store_id, type, scene, status, prompt, provider_task_id, result_url, cost, "
            + "error_message, refund_status, started_at, finished_at, created_at, updated_at FROM media_task "
            + "WHERE status = 'FAILED' AND refund_status = 0 ORDER BY updated_at LIMIT #{limit}")
    List<MediaTask> findFailedWithoutRefund(@Param("limit") int limit);

    @Update("UPDATE media_task SET status = 'PROCESSING', started_at = COALESCE(started_at, NOW()) "
            + "WHERE id = #{id} AND status = 'PENDING'")
    int claimPending(@Param("id") Long id);

    @Update("UPDATE media_task SET status = 'SUBMITTED', provider_task_id = #{providerTaskId}, "
            + "started_at = COALESCE(started_at, NOW()) WHERE id = #{id} AND status = 'PROCESSING'")
    int markSubmitted(@Param("id") Long id, @Param("providerTaskId") String providerTaskId);

    @Update("UPDATE media_task SET status = 'POLLING' WHERE id = #{id} AND status = 'SUBMITTED'")
    int claimSubmitted(@Param("id") Long id);

    @Update("UPDATE media_task SET status = 'SUBMITTED' WHERE id = #{id} AND status = 'POLLING'")
    int resumeSubmitted(@Param("id") Long id);

    @Update("UPDATE media_task SET status = 'SUCCESS', result_url = #{resultUrl}, finished_at = NOW(), error_message = NULL "
            + "WHERE id = #{id} AND status IN ('PROCESSING', 'POLLING')")
    int markSuccess(@Param("id") Long id, @Param("resultUrl") String resultUrl);

    @Update("UPDATE media_task SET status = 'FAILED', error_message = #{errorMessage}, finished_at = NOW() "
            + "WHERE id = #{id} AND status <> 'SUCCESS'")
    int markFailed(@Param("id") Long id, @Param("errorMessage") String errorMessage);

    @Update("UPDATE media_task SET refund_status = 1 WHERE id = #{id} AND status = 'FAILED'")
    int markRefunded(@Param("id") Long id);

    @Select("SELECT id, tenant_id, user_id, work_id, store_id, type, scene, status, prompt, provider_task_id, result_url, cost, "
            + "error_message, refund_status, started_at, finished_at, created_at, updated_at FROM media_task "
            + "WHERE tenant_id = #{tenantId} ORDER BY created_at DESC LIMIT #{limit}")
    List<MediaTask> findForAdmin(@Param("tenantId") Long tenantId, @Param("limit") int limit);
}
