package com.demand.system.module.nl2sql.dto;

import java.util.List;
import java.util.Map;

/**
 * NL2SQL 数据查询结果（随助手消息返回给前端）。
 *
 * @param question      用户原始问题
 * @param sql           实际执行的 SQL（只读，已通过安全校验与数据权限注入）
 * @param columns       结果列定义
 * @param rows          结果行（有序 Map，key 为列名）
 * @param rowCount      返回行数
 * @param truncated     是否因超出上限被截断
 * @param chart         图表建议
 * @param sourceTables  涉及的业务表（用于展示"数据来源"）
 * @param durationMs    SQL 执行耗时（毫秒）
 * @param scopeApplied  是否注入了数据权限范围过滤
 */
public record DataQueryResult(
        String question,
        String sql,
        List<DataQueryColumn> columns,
        List<Map<String, Object>> rows,
        int rowCount,
        boolean truncated,
        DataChartSpec chart,
        List<String> sourceTables,
        long durationMs,
        boolean scopeApplied
) {
}
