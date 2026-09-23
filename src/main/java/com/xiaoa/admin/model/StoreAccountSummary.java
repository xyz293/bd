package com.xiaoa.admin.model;

import com.xiaoa.tenant.model.Org;

public class StoreAccountSummary {

    private final Org store;
    private final long memberCount;
    private final Long ownerUserId;

    public StoreAccountSummary(Org store, long memberCount, Long ownerUserId) {
        this.store = store;
        this.memberCount = memberCount;
        this.ownerUserId = ownerUserId;
    }

    public Org getStore() { return store; }
    public long getMemberCount() { return memberCount; }
    public Long getOwnerUserId() { return ownerUserId; }
}
