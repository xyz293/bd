package com.xiaoa.task.mapper;

import com.xiaoa.task.model.Message;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MessageMapper {

    @Insert("INSERT INTO message (tenant_id, user_id, type, title, content) "
            + "VALUES (#{tenantId}, #{userId}, #{type}, #{title}, #{content})")
    int insert(Message message);
}
