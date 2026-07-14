package com.demand.system.module.bitable.dto;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 多维表格-记录行视图对象
 * cells 由 Service 层单独装配，不参与 MapStruct 自动映射
 */
public class BitableRecordVO {

    private Long id;

    private Long tableId;

    private Integer sortOrder;

    private Long createdBy;

    private String createdByName;

    private LocalDateTime createdAt;

    private Long updatedBy;

    private String updatedByName;

    private LocalDateTime updatedAt;

    private Integer version;

    private Map<Long, BitableCellValueVO> cells;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTableId() {
        return tableId;
    }

    public void setTableId(Long tableId) {
        this.tableId = tableId;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public String getCreatedByName() {
        return createdByName;
    }

    public void setCreatedByName(String createdByName) {
        this.createdByName = createdByName;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(Long updatedBy) {
        this.updatedBy = updatedBy;
    }

    public String getUpdatedByName() {
        return updatedByName;
    }

    public void setUpdatedByName(String updatedByName) {
        this.updatedByName = updatedByName;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public Map<Long, BitableCellValueVO> getCells() {
        return cells;
    }

    public void setCells(Map<Long, BitableCellValueVO> cells) {
        this.cells = cells;
    }
}
