package com.xiaoa.admin.model;

public class DashboardOverview {

    private final long activeStores;
    private final long weeklyWorks;
    private final long weeklyPublishes;
    private final long totalQuota;
    private final long usedQuota;

    public DashboardOverview(long activeStores, long weeklyWorks, long weeklyPublishes,
                             long totalQuota, long usedQuota) {
        this.activeStores = activeStores;
        this.weeklyWorks = weeklyWorks;
        this.weeklyPublishes = weeklyPublishes;
        this.totalQuota = totalQuota;
        this.usedQuota = usedQuota;
    }

    public long getActiveStores() { return activeStores; }
    public long getWeeklyWorks() { return weeklyWorks; }
    public long getWeeklyPublishes() { return weeklyPublishes; }
    public long getTotalQuota() { return totalQuota; }
    public long getUsedQuota() { return usedQuota; }
}
