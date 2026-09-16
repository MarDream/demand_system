package com.demand.system.module.nl2sql.schema;

import com.demand.system.module.nl2sql.config.Nl2SqlProperties;
import com.demand.system.module.nl2sql.dto.SchemaColumnMeta;
import com.demand.system.module.nl2sql.dto.SchemaTableMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Schema 目录服务：从 {@code information_schema} 读取业务表结构，
 * 融合 {@link Nl2SqlSemantics} 的人工语义，产出：
 * <ul>
 *   <li>供 LLM 使用的紧凑 schema 提示词（含中文注释、枚举、关联关系）</li>
 *   <li>供安全校验使用的表/列元信息索引</li>
 * </ul>
 *
 * <p>结果按 {@link Nl2SqlProperties#getSchemaCacheMinutes()} 缓存，避免每次问答都读元数据。</p>
 */
@Service
public class SchemaCatalogService {

    private static final Logger log = LoggerFactory.getLogger(SchemaCatalogService.class);

    private final JdbcTemplate jdbcTemplate;
    private final Nl2SqlProperties properties;

    private volatile CatalogSnapshot cache;
    private volatile Instant cacheExpireAt = Instant.EPOCH;

    public SchemaCatalogService(DataSource dataSource, Nl2SqlProperties properties) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.properties = properties;
    }

    /** 允许查询的表集合（小写）。 */
    public Set<String> allowedTables() {
        List<String> configured = properties.getAllowedTables();
        List<String> source = (configured == null || configured.isEmpty())
                ? Nl2SqlSemantics.defaultAllowedTables()
                : configured;
        Set<String> set = new LinkedHashSet<>();
        source.forEach(t -> set.add(t.toLowerCase(Locale.ROOT)));
        return set;
    }

    /** 当前 schema 快照（带缓存）。 */
    public CatalogSnapshot snapshot() {
        CatalogSnapshot current = cache;
        if (current != null && Instant.now().isBefore(cacheExpireAt)) {
            return current;
        }
        synchronized (this) {
            if (cache != null && Instant.now().isBefore(cacheExpireAt)) {
                return cache;
            }
            CatalogSnapshot built = load();
            cache = built;
            cacheExpireAt = Instant.now().plus(Duration.ofMinutes(Math.max(1, properties.getSchemaCacheMinutes())));
            return built;
        }
    }

    /** 主动失效缓存（表结构变更后调用）。 */
    public void invalidate() {
        cache = null;
        cacheExpireAt = Instant.EPOCH;
    }

    private CatalogSnapshot load() {
        Set<String> allowed = allowedTables();
        Set<String> deniedKeywords = new LinkedHashSet<>();
        if (properties.getDeniedColumnKeywords() != null) {
            properties.getDeniedColumnKeywords().stream()
                    .filter(k -> k != null && !k.isBlank())
                    .forEach(k -> deniedKeywords.add(k.toLowerCase(Locale.ROOT)));
        }

        Map<String, String> tableComments = new LinkedHashMap<>();
        jdbcTemplate.query(
                "SELECT TABLE_NAME, TABLE_COMMENT FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE()",
                rs -> {
                    tableComments.put(rs.getString("TABLE_NAME").toLowerCase(Locale.ROOT), rs.getString("TABLE_COMMENT"));
                }
        );

        // 先从字典表读出真实枚举口径：数据库列注释可能过期（例如 priority 注释写 critical/high，
        // 实际存的是 P0~P3），必须以字典表为准，否则模型会生成永远查不到数据的过滤条件。
        EnumDictionary dictionary = loadDictionary();

        Map<String, List<SchemaColumnMeta>> tableColumns = new LinkedHashMap<>();
        jdbcTemplate.query(
                """
                SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, COLUMN_COMMENT, IS_NULLABLE
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                ORDER BY TABLE_NAME, ORDINAL_POSITION
                """,
                rs -> {
                    String table = rs.getString("TABLE_NAME").toLowerCase(Locale.ROOT);
                    if (!allowed.contains(table)) {
                        return;
                    }
                    String column = rs.getString("COLUMN_NAME");
                    String lowerColumn = column.toLowerCase(Locale.ROOT);
                    if (deniedKeywords.stream().anyMatch(lowerColumn::contains)) {
                        return;
                    }
                    tableColumns.computeIfAbsent(table, k -> new ArrayList<>())
                            .add(new SchemaColumnMeta(
                                    column,
                                    rs.getString("COLUMN_TYPE"),
                                    rs.getString("COLUMN_COMMENT"),
                                    "YES".equalsIgnoreCase(rs.getString("IS_NULLABLE")),
                                    enumValuesOf(table, column, dictionary)
                            ));
                }
        );

        Map<String, SchemaTableMeta> tables = new LinkedHashMap<>();
        for (Map.Entry<String, List<SchemaColumnMeta>> entry : tableColumns.entrySet()) {
            String table = entry.getKey();
            String comment = tableComments.getOrDefault(table, "");
            tables.put(table, new SchemaTableMeta(table, comment, entry.getValue(), businessNote(table)));
        }

        String prompt = buildSchemaPrompt(tables, dictionary);
        log.info("NL2SQL schema 目录已加载：{} 张表，提示词 {} 字符", tables.size(), prompt.length());
        return new CatalogSnapshot(tables, prompt);
    }

    /**
     * 从字典表读取真实枚举口径。
     *
     * <p>字典表可能不存在（全新库）或读取失败，此时降级为空字典 —— 提示词少一段枚举说明，
     * 但不会让整个目录加载失败。</p>
     */
    private EnumDictionary loadDictionary() {
        Map<String, String> priorities = queryDictionary(
                "SELECT code, name FROM priorities ORDER BY sort_order");
        Map<String, String> requirementTypes = queryDictionary(
                "SELECT code, name FROM requirement_types ORDER BY sort_order");

        List<String> statuses = new ArrayList<>();
        Set<String> finalStatuses = new LinkedHashSet<>();
        try {
            jdbcTemplate.query(
                    "SELECT name, is_final FROM workflow_states ORDER BY is_final, sort_order",
                    rs -> {
                        String name = rs.getString("name");
                        if (name != null && !name.isBlank() && !statuses.contains(name)) {
                            statuses.add(name);
                        }
                        if (rs.getInt("is_final") == 1 && name != null && !name.isBlank()) {
                            finalStatuses.add(name);
                        }
                    }
            );
            // 兜底：把需求表里实际出现过的状态也纳入取值列表
            jdbcTemplate.query(
                    "SELECT DISTINCT status FROM requirements WHERE status IS NOT NULL AND status <> ''",
                    rs -> {
                        String status = rs.getString(1);
                        if (status != null && !status.isBlank() && !statuses.contains(status)) {
                            statuses.add(status);
                        }
                    }
            );
        } catch (Exception e) {
            log.warn("NL2SQL 状态字典读取失败，将不附带状态取值说明：{}", e.getMessage());
        }

        return new EnumDictionary(priorities, requirementTypes, statuses, finalStatuses);
    }

    private Map<String, String> queryDictionary(String sql) {
        Map<String, String> map = new LinkedHashMap<>();
        try {
            jdbcTemplate.query(sql, rs -> {
                String code = rs.getString(1);
                if (code != null && !code.isBlank()) {
                    map.put(code, rs.getString(2));
                }
            });
        } catch (Exception e) {
            log.warn("NL2SQL 字典表读取失败（{}）：{}", sql, e.getMessage());
        }
        return map;
    }

    /** 枚举取值：来自字典表（requirements 的 status/priority/type）。 */
    private String enumValuesOf(String table, String column, EnumDictionary dictionary) {
        if (!"requirements".equals(table) || dictionary == null) {
            return null;
        }
        if ("status".equalsIgnoreCase(column)) {
            return dictionary.statuses().isEmpty() ? null : String.join("、", dictionary.statuses());
        }
        if ("priority".equalsIgnoreCase(column)) {
            return renderEnum(dictionary.priorities());
        }
        if ("type".equalsIgnoreCase(column)) {
            return renderEnum(dictionary.requirementTypes());
        }
        return null;
    }

    private String renderEnum(Map<String, String> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        map.forEach((k, v) -> {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(k);
            if (v != null && !v.isBlank()) {
                sb.append('=').append(v);
            }
        });
        return sb.toString();
    }

    private String businessNote(String table) {
        return switch (table) {
            case "requirements" -> "需求主表；软删除 deleted_at，草稿 is_draft=1；状态中文名见 status 枚举，"
                    + "同一状态的工作流英文编码在 node_status";
            case "iterations" -> "迭代/版本；通过 project_id 关联项目";
            case "projects" -> "项目主表；软删除 deleted_at";
            case "users" -> "用户表；只允许查询 real_name/username/id/org_id 等非敏感列";
            case "sys_org" -> "组织/部门树；parent_id 指向上级组织";
            case "reviews" -> "需求评审记录；result 取值 pass/reject/pending";
            case "workflow_transition_records" -> "需求状态流转记录；created_at 为流转时间";
            case "requirement_history" -> "需求字段变更历史；field_name/old_value/new_value";
            default -> null;
        };
    }

    /** 生成供 LLM 使用的紧凑 schema 描述。 */
    private String buildSchemaPrompt(Map<String, SchemaTableMeta> tables, EnumDictionary dictionary) {
        StringBuilder sb = new StringBuilder();
        sb.append("可用数据表（MySQL 8，仅允许 SELECT 查询）：\n");
        for (SchemaTableMeta table : tables.values()) {
            sb.append("- ").append(table.name());
            if (table.comment() != null && !table.comment().isBlank()) {
                sb.append("（").append(table.comment()).append("）");
            }
            if (table.businessNote() != null) {
                sb.append(" —— ").append(table.businessNote());
            }
            sb.append('\n');
            for (SchemaColumnMeta column : table.columns()) {
                sb.append("    · ").append(column.name())
                        .append(" ").append(column.dataType());
                // JSON 列必须显式给出判空口径：模型默认会写 `列 IS NOT NULL`，
                // 而空数组 `[]` 同样满足 IS NOT NULL，于是"上传了附件的工单"会把全部工单查出来。
                if (Nl2SqlSemantics.isJsonColumnType(column.dataType())) {
                    sb.append(' ').append(Nl2SqlSemantics.jsonColumnHint());
                }
                // status / node_status 是同一状态的中英文两种写法，列注释（"当前节点状态"）
                // 完全看不出后者存的是英文编码，用户用编码提问时模型容易选错列。
                if ("requirements".equals(table.name()) && "node_status".equalsIgnoreCase(column.name())) {
                    sb.append(' ').append(Nl2SqlSemantics.nodeStatusColumnHint());
                }
                // 有权威枚举取值时不再渲染列注释：数据库注释可能过期
                // （如 priority 注释写 critical/high/medium/low，与实际 P0~P3 冲突），
                // 两者同时出现会让模型取到错误取值。
                if (column.enumValues() == null
                        && column.comment() != null && !column.comment().isBlank()) {
                    sb.append(" // ").append(column.comment());
                }
                if (column.enumValues() != null) {
                    sb.append(" [取值: ").append(column.enumValues()).append(']');
                }
                sb.append('\n');
            }
        }
        sb.append("\n业务同义词（用户可能这样表达）：\n");
        Nl2SqlSemantics.tableSynonyms().forEach((table, words) -> {
            if (tables.containsKey(table)) {
                sb.append("- ").append(String.join("、", words)).append(" → ").append(table).append('\n');
            }
        });
        sb.append("\n表间关联关系：\n");
        Nl2SqlSemantics.joinHints().forEach(hint -> sb.append("- ").append(hint).append('\n'));

        // 枚举取值以字典表为准（列注释可能过期，不能作为依据）
        sb.append("\n枚举取值（来自系统字典表，必须使用这里的取值，不要臆造）：\n");
        if (dictionary != null) {
            if (!dictionary.statuses().isEmpty()) {
                sb.append("- requirements.status（需求状态）：")
                        .append(String.join("、", dictionary.statuses())).append('\n');
            }
            if (!dictionary.finalStatuses().isEmpty()) {
                sb.append("- 终态状态（视为“已完成”，统计未完成/逾期时需排除）：")
                        .append(String.join("、", dictionary.finalStatuses())).append('\n');
            }
            String priority = renderEnum(dictionary.priorities());
            if (priority != null) {
                sb.append("- requirements.priority（优先级）：").append(priority).append('\n');
            }
            String type = renderEnum(dictionary.requirementTypes());
            if (type != null) {
                sb.append("- requirements.type（需求类型）：").append(type).append('\n');
            }
        }
        sb.append("- reviews.result（评审结果）：")
                .append(renderEnum(Nl2SqlSemantics.reviewResultMeaning())).append('\n');

        sb.append("\n业务口径规则：\n");
        Nl2SqlSemantics.businessRules().forEach(rule -> sb.append("- ").append(rule).append('\n'));
        return sb.toString();
    }

    /**
     * 运行时枚举字典（来自字典表，非硬编码）。
     *
     * @param priorities       优先级 code → 名称
     * @param requirementTypes 需求类型 code → 名称
     * @param statuses         需求状态取值（workflow_states 名称 + 需求表实际取值）
     * @param finalStatuses    终态状态（workflow_states.is_final = 1）
     */
    public record EnumDictionary(Map<String, String> priorities,
                                 Map<String, String> requirementTypes,
                                 List<String> statuses,
                                 Set<String> finalStatuses) {
    }

    /**
     * Schema 快照。
     *
     * @param tables      表名 → 表元信息
     * @param schemaPrompt 供 LLM 的 schema 描述文本
     */
    public record CatalogSnapshot(Map<String, SchemaTableMeta> tables, String schemaPrompt) {

        public SchemaTableMeta table(String name) {
            return name == null ? null : tables.get(name.toLowerCase(Locale.ROOT));
        }

        public boolean containsTable(String name) {
            return name != null && tables.containsKey(name.toLowerCase(Locale.ROOT));
        }

        /** 存在软删除列的表集合 */
        public Set<String> softDeleteTables() {
            Set<String> set = new LinkedHashSet<>();
            tables.forEach((name, meta) -> {
                if (meta.hasSoftDelete()) {
                    set.add(name);
                }
            });
            return set;
        }

        /** 需要行级组织过滤的表集合 */
        public Set<String> orgScopedTables() {
            Set<String> set = new LinkedHashSet<>();
            tables.forEach((name, meta) -> {
                if (meta.hasOrgScope()) {
                    set.add(name);
                }
            });
            return set;
        }
    }
}
