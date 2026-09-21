package com.demand.system.module.bitable.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 多维表格-仪表盘组件实体
 */
@TableName("bitable_dashboard_widgets")
public class BitableDashboardWidget {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long dashboardId;

    /** 组件类型: kpi/bar/line/pie */
    private String type;

    private String title;

    /** 数据源配置 JSON: {tableId, filterConfig, dimension:{fieldId,fieldName,granularity}, metrics:[{fieldId,fieldName,aggregation}], sort, limit, multiSource, sources:[{tableId,filterConfig}](多数据源模式), fieldId/aggregation/groupByFieldId(旧版兼容)} */
    private String dataSourceConfig;

    private String displayConfig;

    /** 布局配置 JSON: {rowId, span, height} */
    private String layoutConfig;

    private Integer sortNo;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getDashboardId() {
        return dashboardId;
    }

    public void setDashboardId(Long dashboardId) {
        this.dashboardId = dashboardId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDataSourceConfig() {
        return dataSourceConfig;
    }

    public void setDataSourceConfig(String dataSourceConfig) {
        this.dataSourceConfig = dataSourceConfig;
    }

    public String getDisplayConfig() {
        return displayConfig;
    }

    public void setDisplayConfig(String displayConfig) {
        this.displayConfig = displayConfig;
    }

    public String getLayoutConfig() {
        return layoutConfig;
    }

    public void setLayoutConfig(String layoutConfig) {
        this.layoutConfig = layoutConfig;
    }

    public Integer getSortNo() {
        return sortNo;
    }

    public void setSortNo(Integer sortNo) {
        this.sortNo = sortNo;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BitableDashboardWidget that = (BitableDashboardWidget) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
