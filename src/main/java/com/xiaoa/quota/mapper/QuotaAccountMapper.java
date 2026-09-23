package com.xiaoa.quota.mapper;

import com.xiaoa.quota.model.QuotaAccount;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface QuotaAccountMapper {

    @Insert("INSERT INTO quota_account (tenant_id, level, owner_id, balance, version) "
            + "VALUES (#{tenantId}, #{level}, #{ownerId}, 0, 0) "
            + "ON DUPLICATE KEY UPDATE id = id")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int ensure(QuotaAccount account);

    @Select("SELECT id, tenant_id, level, owner_id, balance, version, created_at, updated_at "
            + "FROM quota_account WHERE tenant_id = #{tenantId} AND id = #{accountId}")
    QuotaAccount findById(@Param("tenantId") Long tenantId, @Param("accountId") Long accountId);

    @Select("SELECT id, tenant_id, level, owner_id, balance, version, created_at, updated_at "
            + "FROM quota_account WHERE tenant_id = #{tenantId} AND level = #{level} AND owner_id = #{ownerId}")
    QuotaAccount findByOwner(@Param("tenantId") Long tenantId, @Param("level") String level,
                             @Param("ownerId") Long ownerId);

    @Update("UPDATE quota_account SET balance = balance + #{amount}, version = version + 1 "
            + "WHERE tenant_id = #{tenantId} AND id = #{accountId} AND balance + #{amount} >= 0")
    int changeBalance(@Param("tenantId") Long tenantId, @Param("accountId") Long accountId,
                      @Param("amount") Long amount);
}
