package com.xiaoa.ai.chat.mapper;

import com.xiaoa.ai.chat.model.ChatSession;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ChatSessionMapper {

    @Insert("INSERT INTO chat_session (tenant_id, user_id, title, scene, status, context, revise_count) "
            + "VALUES (#{tenantId}, #{userId}, #{title}, #{scene}, #{status}, #{context}, 0)")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(ChatSession session);

    @Select("SELECT id, tenant_id, user_id, title, scene, status, context, revise_count, created_at, updated_at "
            + "FROM chat_session WHERE id = #{id} AND tenant_id = #{tenantId}")
    ChatSession findById(@Param("tenantId") Long tenantId, @Param("id") Long id);

    @Select("SELECT id, tenant_id, user_id, title, scene, status, context, revise_count, created_at, updated_at "
            + "FROM chat_session WHERE tenant_id = #{tenantId} AND user_id = #{userId} "
            + "ORDER BY updated_at DESC LIMIT #{limit}")
    List<ChatSession> findByUser(@Param("tenantId") Long tenantId, @Param("userId") Long userId,
                                 @Param("limit") int limit);

    /** 每轮对话后刷新要素上下文（ON UPDATE CURRENT_TIMESTAMP 同时刷新活跃时间） */
    @Update("UPDATE chat_session SET context = #{context} WHERE id = #{id} AND tenant_id = #{tenantId}")
    int updateContext(@Param("tenantId") Long tenantId, @Param("id") Long id, @Param("context") String context);

    @Update("UPDATE chat_session SET status = 'CLOSED' WHERE id = #{id} AND tenant_id = #{tenantId}")
    int close(@Param("tenantId") Long tenantId, @Param("id") Long id);

    @Update("UPDATE chat_session SET revise_count = revise_count + 1 WHERE id = #{id} AND tenant_id = #{tenantId}")
    int incrReviseCount(@Param("tenantId") Long tenantId, @Param("id") Long id);
}
