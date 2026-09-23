package com.xiaoa.quota.mapper;

import com.xiaoa.quota.model.QuotaFlow;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface QuotaFlowMapper {

    @Insert("INSERT INTO quota_flow (tenant_id, account_id, biz_type, biz_id, amount, balance_after, remark) "
            + "SELECT #{tenantId}, #{accountId}, #{bizType}, #{bizId}, #{amount}, balance, #{remark} "
            + "FROM quota_account WHERE id = #{accountId} AND tenant_id = #{tenantId}")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(QuotaFlow flow);

    @Select("SELECT id, tenant_id, account_id, biz_type, biz_id, amount, balance_after, remark, created_at "
            + "FROM quota_flow WHERE tenant_id = #{tenantId} AND account_id = #{accountId} ORDER BY created_at DESC")
    List<QuotaFlow> findByAccount(@Param("tenantId") Long tenantId, @Param("accountId") Long accountId);

    @Select("SELECT id, tenant_id, account_id, biz_type, biz_id, amount, balance_after, remark, created_at "
            + "FROM quota_flow WHERE tenant_id = #{tenantId} AND account_id = #{accountId} AND biz_id = #{bizId} LIMIT 1")
    QuotaFlow findByAccountBiz(@Param("tenantId") Long tenantId, @Param("accountId") Long accountId,
                               @Param("bizId") String bizId);

    @Select("SELECT COALESCE(SUM(-amount), 0) FROM quota_flow WHERE tenant_id = #{tenantId} "
            + "AND amount < 0 AND created_at >= #{from} AND created_at < #{to}")
    long sumConsumed(@Param("tenantId") Long tenantId, @Param("from") LocalDateTime from,
                     @Param("to") LocalDateTime to);
}
