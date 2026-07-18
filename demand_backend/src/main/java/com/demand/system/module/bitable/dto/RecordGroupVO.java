package com.demand.system.module.bitable.dto;

import java.util.List;

/**
 * 分组查询结果
 */
public class RecordGroupVO {

    /** 分组键值（单选文本/数字/日期等字段的显示值） */
    private String groupKey;

    /** 分组内的记录列表 */
    private List<BitableRecordVO> records;

    /** 分组内记录数 */
    private Integer count;

    public RecordGroupVO() {
    }

    public RecordGroupVO(String groupKey, List<BitableRecordVO> records) {
        this.groupKey = groupKey;
        this.records = records;
        this.count = records != null ? records.size() : 0;
    }

    public String getGroupKey() {
        return groupKey;
    }

    public void setGroupKey(String groupKey) {
        this.groupKey = groupKey;
    }

    public List<BitableRecordVO> getRecords() {
        return records;
    }

    public void setRecords(List<BitableRecordVO> records) {
        this.records = records;
        this.count = records != null ? records.size() : 0;
    }

    public Integer getCount() {
        return count;
    }

    public void setCount(Integer count) {
        this.count = count;
    }
}
