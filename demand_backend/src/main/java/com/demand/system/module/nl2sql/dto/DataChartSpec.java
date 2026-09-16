package com.demand.system.module.nl2sql.dto;

/**
 * 图表建议（由 LLM 在生成 SQL 时一并给出，前端据此渲染）。
 *
 * @param type       图表类型：bar / line / pie / table（table 表示只展示表格）
 * @param title      图表标题
 * @param categoryField 分类轴字段（结果列名）
 * @param valueField    数值轴字段（结果列名）
 * @param seriesField   分组/系列字段（可选，如多系列折线）
 */
public record DataChartSpec(
        String type,
        String title,
        String categoryField,
        String valueField,
        String seriesField
) {
    public static DataChartSpec table(String title) {
        return new DataChartSpec("table", title, null, null, null);
    }

    public boolean isChartable() {
        return type != null
                && !"table".equalsIgnoreCase(type)
                && categoryField != null && !categoryField.isBlank()
                && valueField != null && !valueField.isBlank();
    }
}
