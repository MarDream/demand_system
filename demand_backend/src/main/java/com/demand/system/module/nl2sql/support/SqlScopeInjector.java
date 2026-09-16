package com.demand.system.module.nl2sql.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 行级数据权限与软删除过滤注入器。
 *
 * <p>出于安全考虑，<b>不信任模型自己写的过滤条件</b>：在 SQL 通过结构校验后，
 * 由服务端把"软删除过滤 + 组织数据范围"作为强制谓词注入到最外层 WHERE 中，
 * 并把原有条件整体加括号，避免 {@code OR} 造成优先级泄漏。</p>
 *
 * <p>由于 {@link SqlSafetyValidator} 已保证是"平面 SELECT"（无子查询 / UNION），
 * 所有表引用都在最外层 FROM/JOIN，因此注入到最外层 WHERE 即可覆盖全部数据来源。</p>
 */
public final class SqlScopeInjector {

    private SqlScopeInjector() {
    }

    /**
     * 构造强制谓词列表。
     *
     * @param tables           引用的表（小写）
     * @param aliasByTable     表 → 别名
     * @param softDeleteTables 含 deleted_at 的表
     * @param orgScopedTables  含 org_id 的表
     * @param visibleOrgIds    当前用户可见组织 ID（非超管时非空）
     * @param superAdmin       是否超管
     */
    public static List<String> buildPredicates(Set<String> tables,
                                               Map<String, String> aliasByTable,
                                               Set<String> softDeleteTables,
                                               Set<String> orgScopedTables,
                                               List<Long> visibleOrgIds,
                                               boolean superAdmin) {
        List<String> predicates = new ArrayList<>();
        for (String table : tables) {
            String alias = aliasByTable.getOrDefault(table, table);
            if (softDeleteTables.contains(table)) {
                predicates.add(alias + ".deleted_at = 0");
            }
            if (!superAdmin && orgScopedTables.contains(table) && visibleOrgIds != null && !visibleOrgIds.isEmpty()) {
                predicates.add(alias + ".org_id IN (" + joinIds(visibleOrgIds) + ")");
            }
        }
        return predicates;
    }

    private static String joinIds(List<Long> ids) {
        StringBuilder sb = new StringBuilder();
        for (Long id : ids) {
            if (id == null) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(id);
        }
        return sb.toString();
    }

    /**
     * 将谓词注入到 SQL 最外层 WHERE。
     *
     * @param sql        已通过结构校验的 SQL
     * @param predicates 强制谓词（空则原样返回）
     * @return 注入后的 SQL
     */
    public static String inject(String sql, List<String> predicates) {
        if (predicates == null || predicates.isEmpty() || sql == null || sql.isBlank()) {
            return sql;
        }
        String combined = String.join(" AND ", predicates);
        String masked = SqlTextScanner.maskLiterals(sql);
        List<SqlTextScanner.KeywordHit> whereHits = SqlTextScanner.topLevelHits(masked, "WHERE");
        List<SqlTextScanner.KeywordHit> clauseHits =
                SqlTextScanner.topLevelHits(masked, "GROUP", "ORDER", "LIMIT", "HAVING", "OFFSET");

        if (!whereHits.isEmpty()) {
            SqlTextScanner.KeywordHit where = whereHits.get(0);
            int end = sql.length();
            for (SqlTextScanner.KeywordHit clause : clauseHits) {
                if (clause.start() > where.end()) {
                    end = clause.start();
                    break;
                }
            }
            String existing = sql.substring(where.end(), end).trim();
            if (existing.isEmpty()) {
                return sql.substring(0, where.end()) + " (" + combined + ") " + sql.substring(end);
            }
            return sql.substring(0, where.end())
                    + " (" + combined + ") AND (" + existing + ") "
                    + sql.substring(end);
        }

        int insertAt = sql.length();
        for (SqlTextScanner.KeywordHit clause : clauseHits) {
            insertAt = clause.start();
            break;
        }
        // 去掉插入点之前的尾随空白，避免生成 `... r  WHERE` 这类双空格
        String prefix = stripTrailingWhitespace(sql.substring(0, insertAt));
        return prefix + " WHERE (" + combined + ") " + sql.substring(insertAt);
    }

    private static String stripTrailingWhitespace(String value) {
        int end = value.length();
        while (end > 0 && Character.isWhitespace(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(0, end);
    }
}
