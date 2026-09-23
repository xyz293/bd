package com.xiaoa.admin.service;

import com.xiaoa.admin.mapper.AdminDashboardMapper;
import com.xiaoa.admin.model.DashboardOverview;
import com.xiaoa.admin.model.DashboardTrendPoint;
import com.xiaoa.common.auth.AdminPermissionService;
import com.xiaoa.common.auth.AuthPrincipal;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdminDashboardService {

    private final AdminDashboardMapper dashboardMapper;
    private final AdminPermissionService permissionService;

    public AdminDashboardService(AdminDashboardMapper dashboardMapper, AdminPermissionService permissionService) {
        this.dashboardMapper = dashboardMapper;
        this.permissionService = permissionService;
    }

    @Cacheable(value = "adminDashboard", key = "'overview:' + #root.target.currentTenantId() + ':' + #root.target.currentScopeKey()")
    public DashboardOverview overview() {
        Long tenantId = currentTenantId();
        Long regionId = currentRegionId();
        LocalDate weekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate sevenDaysAgo = LocalDate.now().minusDays(6);
        return new DashboardOverview(
                regionId == null ? dashboardMapper.countActiveStores(tenantId, sevenDaysAgo)
                        : dashboardMapper.countActiveStoresByRegion(tenantId, sevenDaysAgo, regionId),
                regionId == null ? dashboardMapper.countWeeklyWorks(tenantId, weekStart)
                        : dashboardMapper.countWeeklyWorksByRegion(tenantId, weekStart, regionId),
                regionId == null ? dashboardMapper.countWeeklyPublishes(tenantId, weekStart)
                        : dashboardMapper.countWeeklyPublishesByRegion(tenantId, weekStart, regionId),
                regionId == null ? dashboardMapper.sumQuotaBalance(tenantId)
                        : dashboardMapper.sumQuotaBalanceByRegion(tenantId, regionId),
                regionId == null ? dashboardMapper.sumUsedQuota(tenantId, weekStart)
                        : dashboardMapper.sumUsedQuotaByRegion(tenantId, weekStart, regionId));
    }

    @Cacheable(value = "adminDashboard", key = "'trend:' + #root.target.currentTenantId() + ':' + #root.target.currentScopeKey()")
    public List<DashboardTrendPoint> trend() {
        Long tenantId = currentTenantId();
        LocalDate from = LocalDate.now().minusDays(6);
        Long regionId = currentRegionId();
        Map<LocalDate, long[]> values = new HashMap<>();
        List<DashboardTrendPoint> points = regionId == null
                ? dashboardMapper.trend(tenantId, from)
                : dashboardMapper.trendByRegion(tenantId, from, regionId);
        for (DashboardTrendPoint point : points) {
            long[] value = values.computeIfAbsent(point.getDate(), key -> new long[2]);
            value[0] += point.getWorks();
            value[1] += point.getPublishes();
        }
        List<DashboardTrendPoint> result = new ArrayList<>();
        for (int index = 0; index < 7; index++) {
            LocalDate date = from.plusDays(index);
            long[] value = values.getOrDefault(date, new long[2]);
            result.add(new DashboardTrendPoint(date, value[0], value[1]));
        }
        return result;
    }

    public Long currentTenantId() {
        return permissionService.requiredRead().getTenantId();
    }

    public String currentScopeKey() {
        AuthPrincipal principal = permissionService.requiredRead();
        return "REGION_ADMIN".equals(principal.getRole()) ? String.valueOf(principal.getOrgId()) : "ALL";
    }

    private Long currentRegionId() {
        AuthPrincipal principal = permissionService.requiredRead();
        return "REGION_ADMIN".equals(principal.getRole()) ? principal.getOrgId() : null;
    }
}
