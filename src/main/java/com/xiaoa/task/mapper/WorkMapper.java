package com.xiaoa.task.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface WorkMapper {

    @Select("SELECT COUNT(*) FROM `work` WHERE id = #{workId} AND tenant_id = #{tenantId} "
            + "AND user_id = #{userId}")
    int countOwned(@Param("tenantId") Long tenantId, @Param("workId") Long workId,
                   @Param("userId") Long userId);
}
