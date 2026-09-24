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
            + "user_input, ref_image_urls, prompt_template_id, prompt_template_version, status, copywriting, "
            + "source_asset_ids) "
            + "VALUES (#{tenantId}, #{userId}, #{type}, #{mediaTaskId}, #{platform}, #{styleId}, #{styleName}, "
            + "#{userInput}, #{refImageUrls}, #{promptTemplateId}, #{promptTemplateVersion}, #{status}, #{copywriting}, "
            + "#{sourceAssetIds})")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(Work work);

    @Select("SELECT id, tenant_id, user_id, type, media_task_id, platform, style_id, style_name, user_input, "
            + "ref_image_urls, prompt_template_id, prompt_template_version, content_url, copywriting, status, "
            + "fail_reason, publish_status, caption, source_asset_ids, created_at, updated_at "
            + "FROM `work` WHERE id = #{id} AND tenant_id = #{tenantId}")
    Work findById(@Param("id") Long id, @Param("tenantId") Long tenantId);

    @Select("SELECT id, tenant_id, user_id, type, media_task_id, platform, style_id, style_name, user_input, "
            + "ref_image_urls, prompt_template_id, prompt_template_version, content_url, copywriting, status, "
            + "fail_reason, publish_status, caption, source_asset_ids, created_at, updated_at "
            + "FROM `work` WHERE tenant_id = #{tenantId} AND publish_status = 'PENDING_AUDIT' AND status = 'SUCCESS' "
            + "ORDER BY updated_at DESC LIMIT #{limit}")
    java.util.List<Work> findPendingAudit(@Param("tenantId") Long tenantId, @Param("limit") int limit);

    @Update("UPDATE `work` SET media_task_id = #{taskId} WHERE id = #{workId} AND tenant_id = #{tenantId}")
    int bindTask(@Param("tenantId") Long tenantId, @Param("workId") Long workId, @Param("taskId") Long taskId);

    @Update("UPDATE `work` SET status = 'SUCCESS', content_url = #{contentUrl}, fail_reason = NULL "
            + "WHERE id = #{workId} AND tenant_id = #{tenantId}")
    int markSuccess(@Param("tenantId") Long tenantId, @Param("workId") Long workId,
                    @Param("contentUrl") String contentUrl);

    @Update("UPDATE `work` SET status = 'FAILED', fail_reason = #{reason} WHERE id = #{workId} AND tenant_id = #{tenantId}")
    int markFailed(@Param("tenantId") Long tenantId, @Param("workId") Long workId,
                   @Param("reason") String reason);

    /**
     * 生成成功后按审核开关初始化发布状态，仅在初始态 NONE 时生效，防止覆盖并发审核结果。
     */
    @Update("UPDATE `work` SET publish_status = #{publishStatus} "
            + "WHERE id = #{workId} AND tenant_id = #{tenantId} AND publish_status = 'NONE'")
    int initPublishStatus(@Param("tenantId") Long tenantId, @Param("workId") Long workId,
                          @Param("publishStatus") String publishStatus);

    /**
     * 发布维度状态机的条件更新守卫：行数为 0 表示状态已被并发操作变更。
     */
    @Update("UPDATE `work` SET publish_status = #{toStatus} "
            + "WHERE id = #{workId} AND tenant_id = #{tenantId} AND publish_status = #{fromStatus}")
    int transitPublishStatus(@Param("tenantId") Long tenantId, @Param("workId") Long workId,
                             @Param("fromStatus") String fromStatus, @Param("toStatus") String toStatus);

    @Update("UPDATE `work` SET caption = #{caption} "
            + "WHERE id = #{workId} AND tenant_id = #{tenantId}")
    int updateCaption(@Param("tenantId") Long tenantId, @Param("workId") Long workId,
                      @Param("caption") String caption);

    /**
     * 成套文案异步回填：仅当文案为空时写入，避免覆盖员工手改内容。
     */
    @Update("UPDATE `work` SET caption = #{caption} "
            + "WHERE id = #{workId} AND tenant_id = #{tenantId} AND (caption IS NULL OR caption = '')")
    int fillCaptionIfAbsent(@Param("tenantId") Long tenantId, @Param("workId") Long workId,
                            @Param("caption") String caption);

    /**
     * 重新生成：作品回到生成中，发布状态重置为 NONE；仅允许成功且处于可改稿状态的作品。
     */
    @Update("UPDATE `work` SET status = 'PENDING', content_url = NULL, fail_reason = NULL, publish_status = 'NONE' "
            + "WHERE id = #{workId} AND tenant_id = #{tenantId} AND status = 'SUCCESS' "
            + "AND publish_status IN ('DRAFT', 'APPROVED', 'REJECTED')")
    int backToGenerating(@Param("tenantId") Long tenantId, @Param("workId") Long workId);
}
