package com.xiaoa.quota.mapper;

import com.xiaoa.quota.model.QuotaReconcileAlert;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface QuotaReconcileMapper {

    @Select("SELECT DISTINCT tenant_id FROM quota_account ORDER BY tenant_id")
    List<Long> findTenantIds();

    @Insert("INSERT INTO quota_reconcile_alert (tenant_id, check_type, expected_value, actual_value, detail) "
            + "VALUES (#{tenantId}, #{checkType}, #{expectedValue}, #{actualValue}, #{detail})")
    int insertAlert(QuotaReconcileAlert alert);
}
