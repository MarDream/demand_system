package com.demand.system.common.result;

import java.util.List;

public class PageResult<T> {

    private List<T> list;
    private long total;
    private int pageNum;
    private int pageSize;
    /** 游标分页时返回下一页游标值，OFFSET 分页时为 null */
    private String nextCursor;
    /** 游标分页时指示是否还有更多数据，OFFSET 分页时为 null */
    private Boolean hasMore;

    public PageResult() {
    }

    public PageResult(List<T> list, long total, int pageNum, int pageSize) {
        this.list = list;
        this.total = total;
        this.pageNum = pageNum;
        this.pageSize = pageSize;
    }

    public PageResult(List<T> list, long total, int pageNum, int pageSize, String nextCursor, Boolean hasMore) {
        this.list = list;
        this.total = total;
        this.pageNum = pageNum;
        this.pageSize = pageSize;
        this.nextCursor = nextCursor;
        this.hasMore = hasMore;
    }

    public List<T> getList() {
        return list;
    }

    public void setList(List<T> list) {
        this.list = list;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public int getPageNum() {
        return pageNum;
    }

    public void setPageNum(int pageNum) {
        this.pageNum = pageNum;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public String getNextCursor() {
        return nextCursor;
    }

    public void setNextCursor(String nextCursor) {
        this.nextCursor = nextCursor;
    }

    public Boolean getHasMore() {
        return hasMore;
    }

    public void setHasMore(Boolean hasMore) {
        this.hasMore = hasMore;
    }
}
