package com.xiaoa.task.mapper;

import com.xiaoa.ai.model.Work;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface WorkMapper {

    @Select("SELECT COUNT(*) FROM `work` WHERE id = #{workId} AND tenant_id = #{tenantId} "
            + "AND user_id = #{userId}")
    int countOwned(@Param("tenantId") Long tenantId, @Param("workId") Long workId,
                   @Param("userId") Long userId);

    @Insert("INSERT INTO `work` (tenant_id, user_id, type, media_task_id, platform, style_id, style_name, "
            + "user_input, ref_image_urls, prompt_template_id, prompt_template_version, status, copywriting) "
            + "VALUES (#{tenantId}, #{userId}, #{type}, #{mediaTaskId}, #{platform}, #{styleId}, #{styleName}, "
            + "#{userInput}, #{refImageUrls}, #{promptTemplateId}, #{promptTemplateVersion}, #{status}, #{copywriting})")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(Work work);

    @Select("SELECT id, tenant_id, user_id, type, media_task_id, platform, style_id, style_name, user_input, "
            + "ref_image_urls, prompt_template_id, prompt_template_version, content_url, copywriting, status, "
            + "fail_reason, created_at, updated_at FROM `work` WHERE id = #{id} AND tenant_id = #{tenantId}")
    Work findById(@Param("id") Long id, @Param("tenantId") Long tenantId);

    @Update("UPDATE `work` SET media_task_id = #{taskId} WHERE id = #{workId} AND tenant_id = #{tenantId}")
    int bindTask(@Param("tenantId") Long tenantId, @Param("workId") Long workId, @Param("taskId") Long taskId);

    @Update("UPDATE `work` SET status = 'SUCCESS', content_url = #{contentUrl}, fail_reason = NULL "
            + "WHERE id = #{workId} AND tenant_id = #{tenantId}")
    int markSuccess(@Param("tenantId") Long tenantId, @Param("workId") Long workId,
                    @Param("contentUrl") String contentUrl);

    @Update("UPDATE `work` SET status = 'FAILED', fail_reason = #{reason} WHERE id = #{workId} AND tenant_id = #{tenantId}")
    int markFailed(@Param("tenantId") Long tenantId, @Param("workId") Long workId,
                   @Param("reason") String reason);
}
