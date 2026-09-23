package com.xiaoa.common.api;

import java.util.Collections;
import java.util.List;

public final class PageResult<T> {

    private final List<T> list;
    private final long total;
    private final int pageNo;
    private final int pageSize;

    private PageResult(List<T> list, long total, int pageNo, int pageSize) {
        this.list = list == null ? Collections.emptyList() : list;
        this.total = total;
        this.pageNo = pageNo;
        this.pageSize = pageSize;
    }

    public static <T> PageResult<T> of(List<T> list, long total, int pageNo, int pageSize) {
        return new PageResult<>(list, total, pageNo, pageSize);
    }

    public List<T> getList() {
        return list;
    }

    public long getTotal() {
        return total;
    }

    public int getPageNo() {
        return pageNo;
    }

    public int getPageSize() {
        return pageSize;
    }
}
