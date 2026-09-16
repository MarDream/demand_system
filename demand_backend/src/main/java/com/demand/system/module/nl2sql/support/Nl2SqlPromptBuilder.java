package com.demand.system.module.nl2sql.support;

import com.demand.system.module.assistant.dto.AssistantPageContext;
import com.demand.system.module.knowledge.dto.ConversationTurn;
import com.demand.system.module.nl2sql.dto.DataQueryResult;
import com.demand.system.module.nl2sql.schema.Nl2SqlSemantics;

import java.util.List;
import java.util.Map;

/**
 * NL2SQL 提示词构造器。
 *
 * <p>把"库表结构 + 业务口径 + 少样本 + 对话历史 + 数据权限边界"组装成模型提示词，
 * 使模型在受控范围内生成 SQL，而不是自由发挥。</p>
 */
public final class Nl2SqlPromptBuilder {

    private Nl2SqlPromptBuilder() {
    }

    /** SQL 生成阶段的系统提示词。 */
    public static String buildSqlSystemPrompt(String schemaPrompt,
                                              boolean superAdmin,
                                              int maxRows) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是企业需求管理系统的数据查询专家（NL2SQL）。你的任务是把用户的自然语言问题翻译成一条**只读** MySQL 8 查询，并给出图表建议。\n\n");
        sb.append("硬性规则（违反即视为失败）：\n")
          .append("1. 只能输出一条 SELECT 语句；禁止 INSERT/UPDATE/DELETE/REPLACE/DDL/存储过程；\n")
          .append("2. 禁止子查询、UNION、CTE(WITH)、派生表；需要多表时只用 JOIN + 聚合（这是为了保证数据权限过滤完备）；\n")
          .append("3. 禁止跨库访问，禁止出现 information_schema / mysql / performance_schema / sys 等系统库；\n")
          .append("4. 只能使用下面列出的表与列，禁止臆造表名或列名；\n")
          .append("5. 每个表都要起简短别名（如 r、p、u、i），JOIN 必须写 ON 条件；\n")
          .append("6. 每个结果列都要用中文别名（如 `状态`、`数量`），别名唯一且简短，便于前端展示；\n")
          .append("7. 时间、状态、逾期等口径严格按“业务口径规则”执行；\n")
          .append("8. 必须显式列出需要查询的列，禁止使用 SELECT * 或 别名.*；\n")
          .append("9. 禁止查询密码、密钥、token 等敏感列；\n")
          .append("10. 必须带 LIMIT，且不超过 ").append(maxRows).append("；\n")
          .append("11. 只输出 JSON，不要输出任何解释性文字或 Markdown 代码块标记。\n\n");

        sb.append("输出 JSON 结构：\n")
          .append("{\n")
          .append("  \"needDatabase\": true 或 false,\n")
          .append("  \"reasoning\": \"一句话说明取数思路\",\n")
          .append("  \"sql\": \"SELECT ...\",\n")
          .append("  \"title\": \"本次查询的简短标题\",\n")
          .append("  \"chart\": {\"type\": \"bar|line|pie|table\", \"title\": \"图表标题\", \"categoryField\": \"结果中的分类列名\", \"valueField\": \"结果中的数值列名\", \"seriesField\": null},\n")
          .append("  \"answerHint\": \"回答要点提示\"\n")
          .append("}\n")
          .append("needDatabase 判定口径（务必严格按此执行）：\n")
          .append("  · 只要问题是在问**业务数据本身**（需求/工单/迭代/评审/项目/用户/组织的数量、明细、列表、分布、排名、趋势、是否逾期、谁负责等），一律 needDatabase=true；\n")
          .append("  · 问题里出现**字段名或英文枚举编码**（如 PENDING_CONFIRM、node_status、status、priority）**不影响判定**，仍然是取数问题；不要因为不认识某个编码就把它当成「系统使用问题」；\n")
          .append("  · 只有当问题纯粹在问**系统怎么用**（如何新建需求、入口在哪、为什么报错、怎么配置）时才 needDatabase=false，并把 sql 置为空字符串；\n")
          .append("  · 拿不准时**优先取数**（needDatabase=true），让空结果本身来回答，不要退回操作导航。\n")
          .append("  例：{\"needDatabase\": true} ← “有哪些 PENDING_CONFIRM 的需求”；")
          .append("{\"needDatabase\": false} ← “如何新建需求”。\n\n");

        sb.append("数据权限边界：当前用户为")
          .append(superAdmin ? "超级管理员，可查询全部数据。" : "普通用户，后端会自动追加组织范围过滤，你无需（也不要）自己编写 org_id 条件。")
          .append("\n\n");

        sb.append(schemaPrompt).append("\n");

        sb.append("\n参考示例：\n");
        for (Nl2SqlSemantics.FewShot shot : Nl2SqlSemantics.fewShots()) {
            sb.append("问题：").append(shot.question()).append('\n');
            sb.append("SQL：").append(shot.sql()).append("\n\n");
        }
        return sb.toString();
    }

    /** SQL 生成阶段的用户提示词。 */
    public static String buildSqlUserPrompt(String question,
                                            List<ConversationTurn> history,
                                            AssistantPageContext pageContext,
                                            String retryFeedback) {
        StringBuilder sb = new StringBuilder();
        sb.append("用户问题：").append(question == null ? "" : question.trim()).append('\n');
        if (history != null && !history.isEmpty()) {
            sb.append("\n最近对话（用于消解指代）：\n");
            int start = Math.max(0, history.size() - 6);
            for (int i = start; i < history.size(); i++) {
                ConversationTurn turn = history.get(i);
                if (turn == null || turn.content() == null || turn.content().isBlank()) {
                    continue;
                }
                sb.append("- ").append("user".equalsIgnoreCase(turn.role()) ? "用户" : "助手")
                  .append("：").append(shorten(turn.content(), 200)).append('\n');
            }
        }
        if (pageContext != null && (pageContext.getPageTitle() != null || pageContext.getRoute() != null)) {
            sb.append("\n当前页面：").append(nullToEmpty(pageContext.getPageTitle()))
              .append("（").append(nullToEmpty(pageContext.getRoute())).append("）\n");
            if (pageContext.getEntityType() != null && pageContext.getEntityId() != null) {
                sb.append("当前业务对象：").append(pageContext.getEntityType())
                  .append('#').append(pageContext.getEntityId()).append('\n');
            }
        }
        if (retryFeedback != null && !retryFeedback.isBlank()) {
            sb.append("\n上一次生成的 SQL 未通过安全校验，原因：").append(retryFeedback)
              .append("\n请严格按规则重新生成，务必避免子查询、UNION、CTE 与未授权表。\n");
        }
        sb.append("\n请直接输出 JSON。\n");
        return sb.toString();
    }

    /** 回答生成阶段的系统提示词。 */
    public static String buildAnswerSystemPrompt(boolean superAdmin) {
        return """
                你是企业需求管理系统的数据分析助手。下面会给你用户问题、已执行的只读 SQL 和查询结果（JSON）。
                请用简洁、专业的中文给出“整合性”回答：
                1. 先用 1~2 句话直接回答用户问题，点明关键数字或结论，不要复述问题。
                2. 若结果包含多个维度，用 Markdown 表格或要点总结最重要的若干项（最多 8 行），并按数值排序。
                3. 主动指出有价值的数据特征：最大/最小、占比、集中度、趋势方向、异常值。
                4. 严禁编造结果中不存在的数据；结果为空时，直接说明没有符合条件的数据。
                5. 只能引用“已执行的 SQL”里出现过的表名与列名，禁止臆造不存在的表（例如凭空建议去查某张表）。
                   若确实需要给出排查建议，只能针对已执行的 SQL 本身（如放宽某个过滤条件、核对字段的写入方式），
                   并措辞为“建议核对/确认”，不要断言某张表存在、也不要断言某处一定有数据。
                6. 不要输出 SQL，不要输出 JSON，不要提及“根据提供的数据”这类套话。
                7. 若结果被截断或受数据权限范围限制，需在结尾用一句话如实说明。
                """ + (superAdmin ? "" : "\n8. 本次查询已按当前用户的数据权限范围过滤，如结果偏少可提示用户其可见范围有限。\n");
    }

    /** 回答生成阶段的用户提示词。 */
    public static String buildAnswerUserPrompt(String question,
                                               DataQueryResult result,
                                               int summaryRows,
                                               boolean truncated,
                                               boolean scopeApplied) {
        StringBuilder sb = new StringBuilder();
        sb.append("用户问题：").append(question == null ? "" : question.trim()).append('\n');
        sb.append("执行的 SQL：").append(result == null ? "" : result.sql()).append('\n');
        sb.append("结果行数：").append(result == null ? 0 : result.rowCount()).append('\n');
        if (truncated) {
            sb.append("注意：结果已按上限截断，仅展示前若干行。\n");
        }
        if (scopeApplied) {
            sb.append("注意：结果已按当前用户的数据权限（组织范围）过滤。\n");
        }
        sb.append("\n查询结果（JSON，最多展示 ").append(summaryRows).append(" 行）：\n");
        if (result == null || result.rows().isEmpty()) {
            sb.append("[]（无数据）\n");
        } else {
            List<Map<String, Object>> rows = result.rows();
            int limit = Math.min(summaryRows, rows.size());
            sb.append(toJsonLike(rows.subList(0, limit))).append('\n');
        }
        sb.append("\n请按要求给出整合性中文回答。\n");
        return sb.toString();
    }

    /** 轻量 JSON 序列化（避免在提示词构造器中引入额外依赖行为差异）。 */
    private static String toJsonLike(List<Map<String, Object>> rows) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < rows.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('{');
            boolean first = true;
            for (Map.Entry<String, Object> entry : rows.get(i).entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                sb.append('"').append(escape(entry.getKey())).append("\":");
                Object value = entry.getValue();
                if (value == null) {
                    sb.append("null");
                } else if (value instanceof Number || value instanceof Boolean) {
                    sb.append(value);
                } else {
                    sb.append('"').append(escape(String.valueOf(value))).append('"');
                }
            }
            sb.append('}');
        }
        return sb.append(']').toString();
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ");
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String shorten(String value, int maxLen) {
        if (value == null) {
            return "";
        }
        String compact = value.replaceAll("\\s+", " ").trim();
        return compact.length() <= maxLen ? compact : compact.substring(0, maxLen) + "…";
    }
}
