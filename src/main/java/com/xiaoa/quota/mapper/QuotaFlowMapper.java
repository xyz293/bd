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

    @Insert("INSERT IGNORE INTO quota_flow (tenant_id, account_id, biz_type, biz_id, amount, balance_after, idempotent_key, remark) "
            + "VALUES (#{tenantId}, #{accountId}, #{bizType}, #{bizId}, #{amount}, 0, #{idempotentKey}, #{remark})")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(QuotaFlow flow);

    @Select("SELECT id, tenant_id, account_id, biz_type, biz_id, amount, balance_after, idempotent_key, remark, created_at "
            + "FROM quota_flow WHERE id = #{id} AND tenant_id = #{tenantId}")
    QuotaFlow findById(@Param("tenantId") Long tenantId, @Param("id") Long id);

    @Select("SELECT id, tenant_id, account_id, biz_type, biz_id, amount, balance_after, idempotent_key, remark, created_at "
            + "FROM quota_flow WHERE tenant_id = #{tenantId} AND account_id = #{accountId} "
            + "AND (#{bizType} IS NULL OR biz_type = #{bizType}) "
            + "AND (#{from} IS NULL OR created_at >= #{from}) "
            + "AND (#{to} IS NULL OR created_at < #{to}) "
            + "ORDER BY id DESC LIMIT #{offset}, #{limit}")
    List<QuotaFlow> findByAccount(@Param("tenantId") Long tenantId, @Param("accountId") Long accountId,
                                  @Param("bizType") String bizType, @Param("from") LocalDateTime from,
                                  @Param("to") LocalDateTime to, @Param("offset") int offset,
                                  @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM quota_flow WHERE tenant_id = #{tenantId} AND account_id = #{accountId} "
            + "AND (#{bizType} IS NULL OR biz_type = #{bizType}) "
            + "AND (#{from} IS NULL OR created_at >= #{from}) "
            + "AND (#{to} IS NULL OR created_at < #{to})")
    long countByAccount(@Param("tenantId") Long tenantId, @Param("accountId") Long accountId,
                        @Param("bizType") String bizType, @Param("from") LocalDateTime from,
                        @Param("to") LocalDateTime to);

    @Select("SELECT id, tenant_id, account_id, biz_type, biz_id, amount, balance_after, idempotent_key, remark, created_at "
            + "FROM quota_flow WHERE idempotent_key = #{idempotentKey} LIMIT 1")
    QuotaFlow findByIdempotentKey(@Param("idempotentKey") String idempotentKey);

    @Select("SELECT id, tenant_id, account_id, biz_type, biz_id, amount, balance_after, idempotent_key, remark, created_at "
            + "FROM quota_flow WHERE tenant_id = #{tenantId} AND account_id = #{accountId} "
            + "AND idempotent_key = #{idempotentKey} LIMIT 1")
    QuotaFlow findByAccountIdempotentKey(@Param("tenantId") Long tenantId, @Param("accountId") Long accountId,
                                        @Param("idempotentKey") String idempotentKey);

    @org.apache.ibatis.annotations.Update("UPDATE quota_flow SET balance_after = #{balanceAfter} "
            + "WHERE id = #{flowId} AND tenant_id = #{tenantId}")
    int updateBalanceAfter(@Param("tenantId") Long tenantId, @Param("flowId") Long flowId,
                           @Param("balanceAfter") Long balanceAfter);

    @Select("SELECT COALESCE(SUM(-amount), 0) FROM quota_flow WHERE tenant_id = #{tenantId} "
            + "AND amount < 0 AND created_at >= #{from} AND created_at < #{to}")
    long sumConsumed(@Param("tenantId") Long tenantId, @Param("from") LocalDateTime from,
                     @Param("to") LocalDateTime to);

    @Select("SELECT balance_after FROM quota_flow WHERE account_id = #{accountId} "
            + "ORDER BY id DESC LIMIT 1")
    Long findLatestBalanceAfter(@Param("accountId") Long accountId);

    @Select("SELECT COALESCE(SUM(amount), 0) FROM quota_flow WHERE tenant_id = #{tenantId}")
    long sumAmount(@Param("tenantId") Long tenantId);

    @Select("SELECT COALESCE(SUM(amount), 0) FROM quota_flow WHERE tenant_id = #{tenantId} "
            + "AND biz_type = 'CREDIT'")
    long sumCredit(@Param("tenantId") Long tenantId);

    @Select("SELECT COUNT(*) FROM quota_flow WHERE tenant_id = #{tenantId} AND biz_type = 'REFUND'")
    long countRefund(@Param("tenantId") Long tenantId);
}
