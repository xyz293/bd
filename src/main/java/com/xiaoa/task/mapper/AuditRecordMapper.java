package com.xiaoa.task.mapper;

import com.xiaoa.task.model.AuditRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AuditRecordMapper {

    @Insert("INSERT INTO audit_record (tenant_id, work_id, reviewer_id, status, opinion, reviewed_at) "
            + "VALUES (#{tenantId}, #{workId}, #{reviewerId}, #{status}, #{opinion}, NOW())")
    int insert(@Param("tenantId") Long tenantId, @Param("workId") Long workId,
               @Param("reviewerId") Long reviewerId, @Param("status") String status,
               @Param("opinion") String opinion);

    @Select("SELECT id, tenant_id, work_id, reviewer_id, status, opinion, reviewed_at, created_at, updated_at "
            + "FROM audit_record WHERE tenant_id = #{tenantId} AND work_id = #{workId} "
            + "AND status <> 'PENDING' ORDER BY id DESC LIMIT 1")
    AuditRecord findLatestDecided(@Param("tenantId") Long tenantId, @Param("workId") Long workId);
}
