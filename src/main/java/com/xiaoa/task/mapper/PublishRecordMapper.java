package com.xiaoa.task.mapper;

import com.xiaoa.task.model.PublishRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;

@Mapper
public interface PublishRecordMapper {

    @Insert("INSERT INTO publish_record (tenant_id, work_id, user_id, task_id, platform, proof_url) "
            + "VALUES (#{tenantId}, #{workId}, #{userId}, #{taskId}, #{platform}, #{proofUrl})")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(PublishRecord record);
}
