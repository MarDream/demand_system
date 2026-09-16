package com.demand.system.module.nl2sql.schema;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * NL2SQL 业务语义库（人工维护）。
 *
 * <p>表/列的中文注释来自数据库 information_schema，但机器生成的注释往往缺少
 * "业务口径"（例如"进行中"到底对应哪些状态值、"逾期"怎么判定）。这里补充：
 * <ul>
 *   <li>允许查询的业务表白名单（最小权限原则，绝不暴露认证/密钥相关表）</li>
 *   <li>业务同义词（用户口语 → 表名/列名）</li>
 *   <li>枚举值口径（状态/优先级/类型的中文含义）</li>
 *   <li>表间关联关系与常用聚合口径</li>
 *   <li>少样本示例（few-shot），显著提升 SQL 生成准确率</li>
 * </ul>
 *
 * <p>如需调整口径，直接修改本类的常量即可，无需改数据库。</p>
 */
public final class Nl2SqlSemantics {

    private Nl2SqlSemantics() {
    }

    /** 允许查询的业务表（最小权限集合）。 */
    public static List<String> defaultAllowedTables() {
        return List.of(
                // 需求域
                "requirements", "requirement_types", "priorities", "iterations",
                "requirement_comments", "requirement_history", "requirement_follows",
                "requirement_relations", "reviews",
                // 项目与组织
                "projects", "project_members", "sys_org", "positions", "users",
                // 工作流
                "workflow_states", "workflow_transition_records", "workflow_instances",
                // 知识库
                "knowledge_bases", "knowledge_documents",
                // 其他
                "file_records", "custom_fields", "requirement_custom_field_values"
        );
    }

    /**
     * 业务同义词：用户口语 → 规范表名。
     * 用于意图识别与提示词增强（提示词中直接列出，帮助模型选对表）。
     */
    public static Map<String, List<String>> tableSynonyms() {
        Map<String, List<String>> map = new LinkedHashMap<>();
        map.put("requirements", List.of("需求", "工单", "需求单", "需求列表", "任务", "条目"));
        map.put("iterations", List.of("迭代", "版本", "sprint", "迭代周期"));
        map.put("projects", List.of("项目", "产品线"));
        map.put("users", List.of("用户", "成员", "人员", "同事", "负责人", "开发", "开发人员", "测试"));
        map.put("sys_org", List.of("组织", "部门", "团队", "机构"));
        map.put("reviews", List.of("评审", "评审记录", "评审结果"));
        map.put("requirement_comments", List.of("评论", "留言", "备注"));
        map.put("requirement_history", List.of("变更历史", "操作记录", "流转历史", "修改记录"));
        map.put("workflow_transition_records", List.of("流转记录", "审批记录", "状态流转", "审批"));
        map.put("knowledge_bases", List.of("知识库"));
        map.put("knowledge_documents", List.of("知识文档", "文档", "资料"));
        map.put("requirement_types", List.of("需求类型", "类型"));
        map.put("priorities", List.of("优先级"));
        map.put("requirement_follows", List.of("关注", "关注人"));
        map.put("requirement_relations", List.of("需求关联", "关联关系"));
        map.put("project_members", List.of("项目成员"));
        return map;
    }

    /*
     * 关于状态 / 优先级 / 类型取值：
     * 这三组取值<b>不再在此硬编码</b>。数据库的列注释是过期的
     * （requirements.priority 注释写着 critical/high/medium/low，实际存的是 P0~P3），
     * 因此真实口径由 {@link SchemaCatalogService} 在运行时从字典表读取：
     *   - priorities         → requirements.priority（P0~P3）
     *   - requirement_types  → requirements.type（如 BUG / REQUIREMENT_DEVELOPER）
     *   - workflow_states    → requirements.status（中文状态名，且因项目工作流而异）
     * 硬编码一旦与线上数据不一致，模型就会生成 `status = 'RELEASED'` 这类永远查不到数据的条件。
     */

    /** 需求评审结果口径（与 reviews.result 列注释一致）。 */
    public static Map<String, String> reviewResultMeaning() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("pass", "通过");
        map.put("reject", "不通过");
        map.put("pending", "待评审");
        return map;
    }

    /**
     * 表间关联关系（供模型生成 JOIN 的依据）。
     */
    public static List<String> joinHints() {
        return List.of(
                "requirements.project_id = projects.id（需求所属项目）",
                "requirements.iteration_id = iterations.id（需求所属迭代）",
                "requirements.assignee_id = users.id（需求负责人）",
                "requirements.creator_id = users.id（需求创建人）",
                "requirements.org_id = sys_org.id（需求归属组织）",
                "requirements.type = requirement_types.code（需求类型编码）",
                "requirements.priority = priorities.code（需求优先级编码）",
                "requirement_comments.requirement_id = requirements.id（需求评论）",
                "requirement_history.requirement_id = requirements.id（需求变更历史）",
                "reviews.requirement_id = requirements.id（需求评审）",
                "reviews.reviewer_id = users.id（评审人）",
                "workflow_transition_records.requirement_id = requirements.id（需求流转记录）",
                "iterations.project_id = projects.id（迭代所属项目）",
                "project_members.project_id = projects.id（项目成员）",
                "project_members.user_id = users.id（成员用户）"
        );
    }

    /**
     * 业务口径说明（自然语言，直接拼进系统提示词）。
     *
     * <p>注意：这里只写"口径规则"，不写具体取值 —— 状态/优先级/类型的真实取值
     * 由 schema 目录在运行时从字典表读出并附在提示词里。</p>
     */
    public static List<String> businessRules() {
        return List.of(
                "所有带 deleted_at 列的表，查询时必须加上 `<别名>.deleted_at = 0` 过滤软删除数据。",
                "requirements.is_draft = 1 表示草稿，统计正式需求时通常要加 `is_draft = 0`。",
                "requirements.status 存的是中文状态名（见上文取值列表），不要臆造英文状态值；"
                        + "各项目工作流不同，状态集合可能略有差异。",
                "“已完成/终态”指 status 落在上文列出的终态集合内（来源 workflow_states.is_final = 1，"
                        + "当前为 已验收 / 已取消 / 已拒绝）。",
                "“逾期需求”指 requirements.due_date IS NOT NULL 且 due_date < CURDATE() "
                        + "且 status 不属于终态集合，且 deleted_at = 0 且 is_draft = 0。",
                "“未完成需求”指 status 不属于终态集合且 deleted_at = 0 且 is_draft = 0。",
                "时间维度统计默认按 requirements.created_at 分组；如需按完成时间可用 development_completed_at 或 confirm_at。",
                "统计数量用 COUNT(*)，统计人数用 COUNT(DISTINCT xxx_id)。",
                "JSON 数组列（如 requirements.attachments）判断“有内容”必须用 `JSON_LENGTH(列) > 0`："
                        + "空数组 `[]` 与 NULL 都不算有内容。"
                        + "写 `列 IS NOT NULL` 或 `列 <> '[]'` 会把空数组误判成有内容，"
                        + "导致“上传了附件的工单”这类问题把全部工单都查出来。",
                "判断 JSON 数组列“没有内容”用 `(列 IS NULL OR JSON_LENGTH(列) = 0)`；"
                        + "要展示数量用 `JSON_LENGTH(列) AS 附件数`。",
                "查询结果列必须使用中文别名（AS），便于直接展示。",
                "禁止查询 users.password 等敏感列；涉及用户只取 real_name、username、id。"
        );
    }

    /**
     * 少样本示例（问题 → SQL）。示例需覆盖常见统计、分组、关联、时间趋势。
     */
    public static List<FewShot> fewShots() {
        return List.of(
                new FewShot(
                        "一共有多少个需求？",
                        "SELECT COUNT(*) AS 需求总数 FROM requirements r WHERE r.deleted_at = 0 AND r.is_draft = 0"
                ),
                new FewShot(
                        "各个状态的需求数量分布",
                        "SELECT r.status AS 状态, COUNT(*) AS 数量 FROM requirements r WHERE r.deleted_at = 0 AND r.is_draft = 0 GROUP BY r.status ORDER BY 数量 DESC"
                ),
                new FewShot(
                        "按优先级统计需求数量",
                        "SELECT r.priority AS 优先级, COUNT(*) AS 数量 FROM requirements r WHERE r.deleted_at = 0 AND r.is_draft = 0 GROUP BY r.priority ORDER BY 数量 DESC"
                ),
                new FewShot(
                        "最近30天每天新增的需求数",
                        "SELECT DATE(r.created_at) AS 日期, COUNT(*) AS 新增需求数 FROM requirements r WHERE r.deleted_at = 0 AND r.is_draft = 0 AND r.created_at >= DATE_SUB(CURDATE(), INTERVAL 30 DAY) GROUP BY DATE(r.created_at) ORDER BY 日期"
                ),
                new FewShot(
                        "每个项目的需求数量，按数量倒序",
                        "SELECT p.name AS 项目名称, COUNT(*) AS 需求数量 FROM requirements r JOIN projects p ON r.project_id = p.id WHERE r.deleted_at = 0 AND r.is_draft = 0 AND p.deleted_at = 0 GROUP BY p.id, p.name ORDER BY 需求数量 DESC"
                ),
                new FewShot(
                        "需求最多的前5个负责人",
                        "SELECT u.real_name AS 负责人, COUNT(*) AS 需求数量 FROM requirements r JOIN users u ON r.assignee_id = u.id WHERE r.deleted_at = 0 AND r.is_draft = 0 GROUP BY u.id, u.real_name ORDER BY 需求数量 DESC LIMIT 5"
                ),
                new FewShot(
                        "哪些需求逾期了？列出编号、标题和截止日期",
                        "SELECT r.requirement_no AS 需求编号, r.title AS 标题, r.due_date AS 截止日期, r.status AS 状态 FROM requirements r WHERE r.deleted_at = 0 AND r.is_draft = 0 AND r.due_date IS NOT NULL AND r.due_date < CURDATE() AND r.status NOT IN ('已验收','已取消','已拒绝') ORDER BY r.due_date ASC"
                ),
                new FewShot(
                        "哪些工单上传了附件？列出编号、标题和附件数",
                        "SELECT r.requirement_no AS 需求编号, r.title AS 标题, r.status AS 状态, JSON_LENGTH(r.attachments) AS 附件数 FROM requirements r WHERE r.deleted_at = 0 AND r.is_draft = 0 AND r.attachments IS NOT NULL AND JSON_LENGTH(r.attachments) > 0 ORDER BY 附件数 DESC"
                ),
                new FewShot(
                        "进行中的迭代有哪些？",
                        "SELECT i.name AS 迭代名称, i.start_date AS 开始日期, i.end_date AS 结束日期, i.status AS 状态 FROM iterations i WHERE i.deleted_at = 0 ORDER BY i.start_date DESC"
                ),
                new FewShot(
                        "需求评审通过率是多少？",
                        "SELECT SUM(CASE WHEN rv.result = 'pass' THEN 1 ELSE 0 END) AS 通过数, COUNT(*) AS 评审总数, ROUND(SUM(CASE WHEN rv.result = 'pass' THEN 1 ELSE 0 END) * 100.0 / NULLIF(COUNT(*),0), 1) AS 通过率百分比 FROM reviews rv"
                ),
                new FewShot(
                        "本月新增需求数和上月相比如何？",
                        "SELECT DATE_FORMAT(r.created_at, '%Y-%m') AS 月份, COUNT(*) AS 新增需求数 FROM requirements r WHERE r.deleted_at = 0 AND r.is_draft = 0 AND r.created_at >= DATE_SUB(DATE_FORMAT(CURDATE(), '%Y-%m-01'), INTERVAL 1 MONTH) GROUP BY DATE_FORMAT(r.created_at, '%Y-%m') ORDER BY 月份"
                )
        );
    }

    /**
     * JSON 列在 schema 提示词里自动追加的判空口径提示。
     *
     * <p>放在语义库里而不是散落在 {@code SchemaCatalogService} 中，一是与其他"业务口径"
     * 集中维护，二是可以被单元测试直接断言（无需连数据库）。</p>
     */
    public static String jsonColumnHint() {
        return "[JSON 列：判“有内容”用 JSON_LENGTH(列) > 0；空数组 [] 与 NULL 都不算有内容]";
    }

    /** 列类型是否为 JSON（information_schema 里 COLUMN_TYPE 形如 {@code json}）。 */
    public static boolean isJsonColumnType(String dataType) {
        return dataType != null && dataType.toLowerCase(java.util.Locale.ROOT).startsWith("json");
    }

    /**
     * {@code requirements.node_status} 列的取值口径提示。
     *
     * <p>{@code status} 与 {@code node_status} 是同一状态的两种写法：前者存中文状态名
     * （与 {@code workflow_states.name} 对齐），后者存工作流节点的英文编码，两列一一对应。
     * 数据库里<b>没有</b>权威的"英文编码 ↔ 中文名"映射表（{@code workflow_states} 只有 name），
     * 所以这里只说明"哪一列存什么"，<b>不列举具体取值</b>，避免再次变成过期的硬编码。</p>
     */
    public static String nodeStatusColumnHint() {
        return "[本列存工作流节点的英文编码，中文状态名在 status 列；用户用英文编码提问时用本列，"
                + "用中文状态名提问时用 status 列；不确定时两列都写 OR 条件]";
    }

    /** few-shot 示例载体。 */
    public record FewShot(String question, String sql) {
    }
}
