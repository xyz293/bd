package com.xiaoa.quota.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MediaTaskReconcileMapper {

    @Select("SELECT COUNT(*) FROM media_task WHERE tenant_id = #{tenantId} "
            + "AND status = 'FAILED' AND refund_status = 1")
    long countFailedRefunded(Long tenantId);
}
