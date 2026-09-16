package com.demand.system.module.nl2sql.dto;

/**
 * 数据库列元信息（供 NL2SQL 提示词与结果展示使用）。
 *
 * @param name      列名
 * @param dataType  数据类型（如 varchar(50)、int）
 * @param comment   列注释（中文业务含义）
 * @param nullable  是否可为空
 * @param enumValues 枚举取值（由语义库或注释解析而来，可为空）
 */
public record SchemaColumnMeta(
        String name,
        String dataType,
        String comment,
        boolean nullable,
        String enumValues
) {
}
