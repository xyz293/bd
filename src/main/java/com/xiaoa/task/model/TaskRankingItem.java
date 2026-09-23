package com.xiaoa.task.model;

import java.time.LocalDateTime;

public class TaskRankingItem {

    private Integer rank;
    private Long userId;
    private String nickname;
    private Long storeId;
    private String storeName;
    private long expected;
    private long finished;
    private LocalDateTime lastFinishedAt;

    public Integer getRank() { return rank; }
    public void setRank(Integer rank) { this.rank = rank; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public Long getStoreId() { return storeId; }
    public void setStoreId(Long storeId) { this.storeId = storeId; }
    public String getStoreName() { return storeName; }
    public void setStoreName(String storeName) { this.storeName = storeName; }
    public long getExpected() { return expected; }
    public void setExpected(long expected) { this.expected = expected; }
    public long getFinished() { return finished; }
    public void setFinished(long finished) { this.finished = finished; }
    public LocalDateTime getLastFinishedAt() { return lastFinishedAt; }
    public void setLastFinishedAt(LocalDateTime lastFinishedAt) { this.lastFinishedAt = lastFinishedAt; }
    public double getCompletionRate() { return expected == 0 ? 0D : (double) finished / expected; }
}
