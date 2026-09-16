package com.demand.system.module.nl2sql.dto;

import java.util.List;

/**
 * 数据库表元信息。
 *
 * @param name       表名
 * @param comment    表注释（中文业务含义）
 * @param columns    列列表
 * @param businessNote 业务口径补充说明（来自语义库）
 */
public record SchemaTableMeta(
        String name,
        String comment,
        List<SchemaColumnMeta> columns,
        String businessNote
) {
    /** 是否包含软删除列 */
    public boolean hasSoftDelete() {
        return columns.stream().anyMatch(c -> "deleted_at".equalsIgnoreCase(c.name()));
    }

    /** 是否包含组织归属列（用于行级数据权限注入） */
    public boolean hasOrgScope() {
        return columns.stream().anyMatch(c -> "org_id".equalsIgnoreCase(c.name()));
    }

    /** 是否包含指定列 */
    public boolean hasColumn(String column) {
        return columns.stream().anyMatch(c -> c.name().equalsIgnoreCase(column));
    }
}
