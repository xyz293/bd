package com.xiaoa.task.model;

public class AdminTaskStoreItem {

    private Long storeId;
    private String storeName;
    private long expected;
    private long finished;

    public Long getStoreId() { return storeId; }
    public void setStoreId(Long storeId) { this.storeId = storeId; }
    public String getStoreName() { return storeName; }
    public void setStoreName(String storeName) { this.storeName = storeName; }
    public long getExpected() { return expected; }
    public void setExpected(long expected) { this.expected = expected; }
    public long getFinished() { return finished; }
    public void setFinished(long finished) { this.finished = finished; }
    public double getCompletionRate() { return expected == 0 ? 0D : (double) finished / expected; }
}
