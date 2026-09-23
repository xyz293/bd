package com.xiaoa.admin.mapper;

import com.xiaoa.admin.model.DashboardTrendPoint;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface AdminDashboardMapper {

    @Select("SELECT COUNT(DISTINCT r.org_id) FROM `work` w "
            + "JOIN user_org_role r ON r.user_id = w.user_id AND r.tenant_id = w.tenant_id "
            + "AND r.status = 1 AND r.role IN ('STAFF', 'OWNER') "
            + "JOIN `org` s ON s.id = r.org_id AND s.tenant_id = w.tenant_id AND s.type = 3 AND s.deleted = 0 "
            + "WHERE w.tenant_id = #{tenantId} AND w.created_at >= #{from}")
    long countActiveStores(@Param("tenantId") Long tenantId, @Param("from") LocalDate from);

    @Select("SELECT COUNT(DISTINCT r.org_id) FROM `work` w "
            + "JOIN user_org_role r ON r.user_id = w.user_id AND r.tenant_id = w.tenant_id "
            + "AND r.status = 1 AND r.role IN ('STAFF', 'OWNER') "
            + "JOIN `org` s ON s.id = r.org_id AND s.tenant_id = w.tenant_id AND s.type = 3 AND s.deleted = 0 "
            + "WHERE w.tenant_id = #{tenantId} AND w.created_at >= #{from} AND s.parent_id = #{regionId}")
    long countActiveStoresByRegion(@Param("tenantId") Long tenantId, @Param("from") LocalDate from,
                                   @Param("regionId") Long regionId);

    @Select("SELECT COUNT(*) FROM `work` WHERE tenant_id = #{tenantId} AND created_at >= #{from}")
    long countWeeklyWorks(@Param("tenantId") Long tenantId, @Param("from") LocalDate from);

    @Select("SELECT COUNT(*) FROM `work` w JOIN user_org_role r ON r.user_id = w.user_id "
            + "AND r.tenant_id = w.tenant_id AND r.status = 1 AND r.role IN ('STAFF', 'OWNER') "
            + "JOIN `org` s ON s.id = r.org_id AND s.tenant_id = w.tenant_id AND s.type = 3 AND s.deleted = 0 "
            + "WHERE w.tenant_id = #{tenantId} AND w.created_at >= #{from} AND s.parent_id = #{regionId}")
    long countWeeklyWorksByRegion(@Param("tenantId") Long tenantId, @Param("from") LocalDate from,
                                  @Param("regionId") Long regionId);

    @Select("SELECT COUNT(*) FROM publish_record WHERE tenant_id = #{tenantId} AND created_at >= #{from}")
    long countWeeklyPublishes(@Param("tenantId") Long tenantId, @Param("from") LocalDate from);

    @Select("SELECT COUNT(*) FROM publish_record p JOIN user_org_role r ON r.user_id = p.user_id "
            + "AND r.tenant_id = p.tenant_id AND r.status = 1 AND r.role IN ('STAFF', 'OWNER') "
            + "JOIN `org` s ON s.id = r.org_id AND s.tenant_id = p.tenant_id AND s.type = 3 AND s.deleted = 0 "
            + "WHERE p.tenant_id = #{tenantId} AND p.created_at >= #{from} AND s.parent_id = #{regionId}")
    long countWeeklyPublishesByRegion(@Param("tenantId") Long tenantId, @Param("from") LocalDate from,
                                      @Param("regionId") Long regionId);

    @Select("SELECT COALESCE(SUM(balance), 0) FROM quota_account WHERE tenant_id = #{tenantId}")
    long sumQuotaBalance(@Param("tenantId") Long tenantId);

    @Select("SELECT COALESCE(SUM(q.balance), 0) FROM quota_account q JOIN `org` s ON s.id = q.owner_id "
            + "AND s.tenant_id = q.tenant_id AND s.type = 3 AND s.deleted = 0 "
            + "WHERE q.tenant_id = #{tenantId} AND q.level = 'STORE' AND s.parent_id = #{regionId}")
    long sumQuotaBalanceByRegion(@Param("tenantId") Long tenantId, @Param("regionId") Long regionId);

    @Select("SELECT COALESCE(SUM(-amount), 0) FROM quota_flow WHERE tenant_id = #{tenantId} "
            + "AND amount < 0 AND created_at >= #{from}")
    long sumUsedQuota(@Param("tenantId") Long tenantId, @Param("from") LocalDate from);

    @Select("SELECT COALESCE(SUM(-f.amount), 0) FROM quota_flow f JOIN quota_account q ON q.id = f.account_id "
            + "AND q.tenant_id = f.tenant_id JOIN `org` s ON s.id = q.owner_id AND s.tenant_id = q.tenant_id "
            + "AND s.type = 3 AND s.deleted = 0 WHERE f.tenant_id = #{tenantId} AND f.amount < 0 "
            + "AND f.created_at >= #{from} AND s.parent_id = #{regionId}")
    long sumUsedQuotaByRegion(@Param("tenantId") Long tenantId, @Param("from") LocalDate from,
                              @Param("regionId") Long regionId);

    @Select("SELECT DATE(created_at) AS date, COUNT(*) AS works, 0 AS publishes FROM `work` "
            + "WHERE tenant_id = #{tenantId} AND created_at >= #{from} GROUP BY DATE(created_at) "
            + "UNION ALL SELECT DATE(created_at) AS date, 0 AS works, COUNT(*) AS publishes FROM publish_record "
            + "WHERE tenant_id = #{tenantId} AND created_at >= #{from} GROUP BY DATE(created_at) ORDER BY date")
    List<DashboardTrendPoint> trend(@Param("tenantId") Long tenantId, @Param("from") LocalDate from);

    @Select("SELECT DATE(w.created_at) AS date, COUNT(*) AS works, 0 AS publishes FROM `work` w "
            + "JOIN user_org_role r ON r.user_id = w.user_id AND r.tenant_id = w.tenant_id "
            + "AND r.status = 1 AND r.role IN ('STAFF', 'OWNER') JOIN `org` s ON s.id = r.org_id "
            + "AND s.tenant_id = w.tenant_id AND s.type = 3 AND s.deleted = 0 "
            + "WHERE w.tenant_id = #{tenantId} AND w.created_at >= #{from} AND s.parent_id = #{regionId} "
            + "GROUP BY DATE(w.created_at) UNION ALL "
            + "SELECT DATE(p.created_at) AS date, 0 AS works, COUNT(*) AS publishes FROM publish_record p "
            + "JOIN user_org_role r ON r.user_id = p.user_id AND r.tenant_id = p.tenant_id "
            + "AND r.status = 1 AND r.role IN ('STAFF', 'OWNER') JOIN `org` s ON s.id = r.org_id "
            + "AND s.tenant_id = p.tenant_id AND s.type = 3 AND s.deleted = 0 "
            + "WHERE p.tenant_id = #{tenantId} AND p.created_at >= #{from} AND s.parent_id = #{regionId} "
            + "GROUP BY DATE(p.created_at) ORDER BY date")
    List<DashboardTrendPoint> trendByRegion(@Param("tenantId") Long tenantId, @Param("from") LocalDate from,
                                             @Param("regionId") Long regionId);
}
