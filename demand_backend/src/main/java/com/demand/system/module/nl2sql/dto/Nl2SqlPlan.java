package com.demand.system.module.nl2sql.dto;

import java.util.List;

/**
 * LLM 输出的 NL2SQL 执行计划（严格 JSON 解析而来）。
 *
 * @param needDatabase 该问题是否需要查询数据库（false 表示应转回知识库/操作导航链路）
 * @param reasoning    判定与取数思路（用于展示"思考过程"）
 * @param sql          生成的只读 SELECT 语句
 * @param title        本次查询的简短标题
 * @param chart        图表建议
 * @param answerHint   回答要点提示（可选，帮助二次总结聚焦）
 * @param usedTables   模型声明使用的表（用于与安全校验结果交叉验证）
 */
public record Nl2SqlPlan(
        boolean needDatabase,
        String reasoning,
        String sql,
        String title,
        DataChartSpec chart,
        String answerHint,
        List<String> usedTables
) {
    public static Nl2SqlPlan notDataQuery(String reasoning) {
        return new Nl2SqlPlan(false, reasoning, null, null, null, null, List.of());
    }
}
