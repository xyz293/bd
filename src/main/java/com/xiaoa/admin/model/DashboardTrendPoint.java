package com.xiaoa.admin.model;

import java.time.LocalDate;

public class DashboardTrendPoint {

    private final LocalDate date;
    private final long works;
    private final long publishes;

    public DashboardTrendPoint(LocalDate date, long works, long publishes) {
        this.date = date;
        this.works = works;
        this.publishes = publishes;
    }

    public LocalDate getDate() { return date; }
    public long getWorks() { return works; }
    public long getPublishes() { return publishes; }
}
