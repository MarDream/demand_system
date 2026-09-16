package com.demand.system.module.nl2sql.dto;

/**
 * 结果集列定义（前端渲染表格表头）。
 *
 * @param field 列名（SQL 别名）
 * @param label 展示名称（默认等于 field）
 * @param type  数据类型：number / date / string
 */
public record DataQueryColumn(String field, String label, String type) {
}
