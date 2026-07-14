package com.demand.system.module.bitable.dto;

import java.util.List;

/**
 * 画廊视图数据
 */
public class GalleryViewData {

    private Long viewId;

    private Long tableId;

    private String viewName;

    private List<BitableFieldVO> fields;

    private List<BitableRecordVO> records;

    public Long getViewId() {
        return viewId;
    }

    public void setViewId(Long viewId) {
        this.viewId = viewId;
    }

    public Long getTableId() {
        return tableId;
    }

    public void setTableId(Long tableId) {
        this.tableId = tableId;
    }

    public String getViewName() {
        return viewName;
    }

    public void setViewName(String viewName) {
        this.viewName = viewName;
    }

    public List<BitableFieldVO> getFields() {
        return fields;
    }

    public void setFields(List<BitableFieldVO> fields) {
        this.fields = fields;
    }

    public List<BitableRecordVO> getRecords() {
        return records;
    }

    public void setRecords(List<BitableRecordVO> records) {
        this.records = records;
    }
}