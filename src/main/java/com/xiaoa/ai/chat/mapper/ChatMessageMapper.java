package com.xiaoa.ai.chat.mapper;

import com.xiaoa.ai.chat.model.ChatMessage;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ChatMessageMapper {

    @Insert("INSERT INTO chat_message (tenant_id, session_id, user_id, role, content, token_count) "
            + "VALUES (#{tenantId}, #{sessionId}, #{userId}, #{role}, #{content}, #{tokenCount})")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(ChatMessage message);

    @Select("SELECT id, tenant_id, session_id, user_id, role, content, token_count, created_at "
            + "FROM chat_message WHERE session_id = #{sessionId} ORDER BY id")
    List<ChatMessage> findBySession(@Param("sessionId") Long sessionId);

    /** 最近 N 条（倒序），供 LLM 上下文组装 */
    @Select("SELECT id, tenant_id, session_id, user_id, role, content, token_count, created_at "
            + "FROM chat_message WHERE session_id = #{sessionId} ORDER BY id DESC LIMIT #{limit}")
    List<ChatMessage> findRecent(@Param("sessionId") Long sessionId, @Param("limit") int limit);

    /** 最近一条 AI 出稿消息（含版本数组），供微调取原文案 */
    @Select("SELECT id, tenant_id, session_id, user_id, role, content, token_count, created_at "
            + "FROM chat_message WHERE session_id = #{sessionId} AND role = 'AI' "
            + "AND content LIKE '%\"action\":\"GENERATE\"%' ORDER BY id DESC LIMIT 1")
    ChatMessage findLatestGenerate(@Param("sessionId") Long sessionId);
}
