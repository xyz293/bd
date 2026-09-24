package com.xiaoa.quota.model;

import java.util.List;

public class QuotaSummary {

    private final QuotaAccount account;
    private final List<QuotaFlow> recentFlows;

    public QuotaSummary(QuotaAccount account, List<QuotaFlow> recentFlows) {
        this.account = account;
        this.recentFlows = recentFlows;
    }

    public QuotaAccount getAccount() {
        return account;
    }

    public List<QuotaFlow> getRecentFlows() {
        return recentFlows;
    }
}
