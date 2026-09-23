package com.xiaoa.admin;

import com.xiaoa.admin.model.DashboardOverview;
import com.xiaoa.admin.model.DashboardTrendPoint;
import com.xiaoa.admin.mapper.AdminDashboardMapper;
import com.xiaoa.admin.service.AdminDashboardService;
import com.xiaoa.common.auth.AdminPermissionService;
import com.xiaoa.common.auth.AuthContext;
import com.xiaoa.common.auth.AuthPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

class EnterpriseAdminServiceTest {

    private final AdminDashboardMapper mapper = Mockito.mock(AdminDashboardMapper.class);
    private final AdminPermissionService permissionService = new AdminPermissionService();
    private final AdminDashboardService service = new AdminDashboardService(mapper, permissionService);

    @AfterEach
    void clearAuth() {
        AuthContext.clear();
    }

    @Test
    void overviewUsesTenantScopedAggregates() {
        AuthContext.set(new AuthPrincipal(10L, 20L, 30L, "HQ_ADMIN", 1));
        Mockito.when(mapper.countActiveStores(eq(20L), any(LocalDate.class))).thenReturn(3L);
        Mockito.when(mapper.countWeeklyWorks(eq(20L), any(LocalDate.class))).thenReturn(8L);
        Mockito.when(mapper.countWeeklyPublishes(eq(20L), any(LocalDate.class))).thenReturn(5L);
        Mockito.when(mapper.sumQuotaBalance(20L)).thenReturn(100L);
        Mockito.when(mapper.sumUsedQuota(eq(20L), any(LocalDate.class))).thenReturn(12L);

        DashboardOverview overview = service.overview();

        assertEquals(3L, overview.getActiveStores());
        assertEquals(8L, overview.getWeeklyWorks());
        assertEquals(5L, overview.getWeeklyPublishes());
        assertEquals(100L, overview.getTotalQuota());
        assertEquals(12L, overview.getUsedQuota());
    }

    @Test
    void trendAlwaysReturnsSevenDays() {
        AuthContext.set(new AuthPrincipal(10L, 20L, 30L, "VIEWER", 1));
        Mockito.when(mapper.trend(eq(20L), any(LocalDate.class))).thenReturn(Collections.emptyList());

        List<DashboardTrendPoint> trend = service.trend();

        assertEquals(7, trend.size());
        assertEquals(0L, trend.get(0).getWorks());
        assertEquals(0L, trend.get(6).getPublishes());
    }
}
