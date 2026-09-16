package com.demand.system.module.nl2sql.support;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * NL2SQL SQL 安全校验器（只读白名单策略）。
 *
 * <p>策略要点（"平面 SELECT"约束）：</p>
 * <ol>
 *   <li>单条语句、必须以 {@code SELECT} 开头，禁止 {@code WITH}/子查询/{@code UNION}
 *       —— 保证所有表引用都出现在最外层 FROM/JOIN，使数据权限注入可被证明是完备的；</li>
 *   <li>表名必须命中白名单；禁止跨库/系统库访问（information_schema / mysql / sys 等）；</li>
 *   <li>关键字黑名单：任何写操作、DDL、文件读写、系统函数、会话变量一律拒绝；</li>
 *   <li>强制 LIMIT，且不超过配置上限。</li>
 * </ol>
 *
 * <p>校验通过后返回规范化 SQL 与"表 → 别名"映射，供数据权限注入使用。</p>
 */
public final class SqlSafetyValidator {

    private SqlSafetyValidator() {
    }

    /** 直接拒绝的单字关键字（写操作 / DDL / 会话控制 / 文件读写）。 */
    private static final List<String> FORBIDDEN_WORDS = List.of(
            "INSERT", "UPDATE", "DELETE", "DROP", "ALTER", "CREATE", "TRUNCATE", "RENAME",
            "GRANT", "REVOKE", "MERGE", "CALL", "EXEC", "EXECUTE", "PREPARE", "DEALLOCATE",
            "HANDLER", "LOCK", "UNLOCK", "SHUTDOWN", "KILL", "INTO", "OUTFILE", "DUMPFILE",
            "LOAD_FILE", "LOAD", "SET", "COMMIT", "ROLLBACK", "SAVEPOINT", "BEGIN", "START",
            "USE", "DESCRIBE", "EXPLAIN", "ANALYZE", "OPTIMIZE", "REPAIR", "FLUSH",
            "RESET", "PURGE", "INSTALL", "UNINSTALL", "DELIMITER", "PROCEDURE", "TRIGGER",
            "EVENT", "TABLESPACE", "USER", "CURRENT_USER", "SESSION_USER", "SYSTEM_USER",
            "DATABASE", "SCHEMA", "SLEEP", "BENCHMARK", "GET_LOCK", "RELEASE_LOCK",
            "INFORMATION_SCHEMA", "PERFORMANCE_SCHEMA", "MYSQL", "SYS"
    );

    /** 需要带括号判断的函数/形式。 */
    private static final List<Pattern> FORBIDDEN_PATTERNS = List.of(
            Pattern.compile("\\bREPLACE\\s+INTO\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bSLEEP\\s*\\(", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bBENCHMARK\\s*\\(", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bUSER\\s*\\(", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bDATABASE\\s*\\(", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bVERSION\\s*\\(", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bLOAD_FILE\\s*\\(", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(MYSQL|SYS|INFORMATION_SCHEMA|PERFORMANCE_SCHEMA)\\s*\\.", Pattern.CASE_INSENSITIVE),
            Pattern.compile("@@")
    );

    /** 关键字命中后不应被当作表别名的保留字。 */
    private static final Set<String> RESERVED_AFTER_TABLE = Set.of(
            "ON", "USING", "WHERE", "GROUP", "ORDER", "LIMIT", "HAVING", "LEFT", "RIGHT",
            "INNER", "OUTER", "CROSS", "JOIN", "STRAIGHT_JOIN", "NATURAL", "FULL", "UNION",
            "FOR", "LOCK", "AS", "SET", "AND", "OR", "WINDOW", "PARTITION", "QUALIFY", "OFFSET"
    );

    private static final Pattern TABLE_REF = Pattern.compile(
            "\\b(FROM|JOIN)\\s+([`\\[\\]A-Za-z0-9_$.]+)(?:\\s+(?:AS\\s+)?([`\\[\\]A-Za-z0-9_$]+))?",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern LIMIT_SIMPLE = Pattern.compile("^\\s*(\\d+)\\s*(?:,\\s*(\\d+))?");
    private static final Pattern LIMIT_OFFSET = Pattern.compile("^\\s*(\\d+)\\s+OFFSET\\s+(\\d+)");

    /**
     * 校验并规范化 SQL。
     *
     * @param rawSql               模型生成的 SQL
     * @param allowedTables        允许的表（小写）
     * @param maxRows              行数上限
     * @param deniedColumnKeywords 禁止出现的敏感列关键字（小写，整词匹配）
     */
    public static ValidationResult validate(String rawSql,
                                           Set<String> allowedTables,
                                           int maxRows,
                                           List<String> deniedColumnKeywords) {
        if (rawSql == null || rawSql.isBlank()) {
            return ValidationResult.invalid("模型未生成 SQL");
        }
        String sql = rawSql.trim();

        // 1. 去掉结尾分号，拒绝多语句
        while (sql.endsWith(";")) {
            sql = sql.substring(0, sql.length() - 1).trim();
        }
        String masked = SqlTextScanner.maskLiterals(sql);
        if (masked.contains(";")) {
            return ValidationResult.invalid("只允许执行单条只读语句");
        }

        // 2. 必须以 SELECT 开头
        String maskedUpper = masked.toUpperCase(Locale.ROOT);
        String trimmedUpper = maskedUpper.stripLeading();
        if (!trimmedUpper.startsWith("SELECT")) {
            return ValidationResult.invalid("只允许 SELECT 查询语句");
        }
        int leadingSpaces = maskedUpper.length() - trimmedUpper.length();

        // 3. 平面查询约束：恰好一个 SELECT，且不含 UNION / CTE
        int selectCount = SqlTextScanner.countWord(masked, "SELECT");
        if (selectCount != 1) {
            return ValidationResult.invalid("暂不支持子查询或多段查询（UNION / CTE / 派生表），请改用 JOIN 与聚合");
        }
        if (SqlTextScanner.containsWord(masked, "UNION")) {
            return ValidationResult.invalid("不允许使用 UNION");
        }
        if (SqlTextScanner.containsWord(masked, "WITH")) {
            return ValidationResult.invalid("不允许使用 CTE（WITH）");
        }
        if (leadingSpaces > 0 && masked.charAt(0) == '(') {
            return ValidationResult.invalid("不允许派生表查询");
        }

        // 4. 关键字黑名单
        for (String word : FORBIDDEN_WORDS) {
            if (SqlTextScanner.containsWord(masked, word)) {
                return ValidationResult.invalid("检测到被禁止的关键字：" + word);
            }
        }
        for (Pattern pattern : FORBIDDEN_PATTERNS) {
            if (pattern.matcher(masked).find()) {
                return ValidationResult.invalid("检测到被禁止的语句形式：" + pattern.pattern());
            }
        }

        // 5. 拒绝隐式逗号连接（`FROM a, b`）：逗号连接的表无法被白名单校验与权限注入覆盖
        if (hasTopLevelCommaJoin(masked)) {
            return ValidationResult.invalid("请使用显式 JOIN ... ON 语法，不要使用逗号连接多张表");
        }

        // 5.1 拒绝敏感列（密码、密钥、token 等）
        // 用"子串命中标识符"而非整词匹配：password_hash / user_password 都必须拦下，
        // 否则模型可以把敏感列别名成普通字段名，绕过结果集侧的列过滤。
        if (deniedColumnKeywords != null) {
            for (String keyword : deniedColumnKeywords) {
                if (keyword == null || keyword.isBlank()) {
                    continue;
                }
                if (SqlTextScanner.containsWordLike(masked, keyword)) {
                    return ValidationResult.invalid("不允许查询敏感字段：" + keyword);
                }
            }
        }

        // 6. 表白名单 + 别名提取
        Map<String, String> aliasByTable = new LinkedHashMap<>();
        Set<String> tables = new LinkedHashSet<>();
        Matcher matcher = TABLE_REF.matcher(masked);
        while (matcher.find()) {
            String rawTable = stripQuotes(matcher.group(2));
            if (rawTable.isEmpty()) {
                continue;
            }
            if (rawTable.contains(".")) {
                return ValidationResult.invalid("不允许跨库或带 schema 前缀访问表：" + rawTable);
            }
            String table = rawTable.toLowerCase(Locale.ROOT);
            if (!allowedTables.contains(table)) {
                return ValidationResult.invalid("表不在允许查询范围内：" + rawTable);
            }
            tables.add(table);

            String alias = matcher.group(3) == null ? null : stripQuotes(matcher.group(3));
            if (alias == null || alias.isBlank()
                    || RESERVED_AFTER_TABLE.contains(alias.toUpperCase(Locale.ROOT))) {
                alias = table;
            }
            aliasByTable.putIfAbsent(table, alias);
        }
        if (tables.isEmpty()) {
            return ValidationResult.invalid("未识别到任何数据表");
        }

        // 6.1 禁止最外层裸 `*`（`SELECT *` / `t.*`），强制显式列清单，避免整行拉取时夹带敏感列
        if (hasTopLevelWildcard(masked)) {
            return ValidationResult.invalid("请显式列出需要查询的列，不要使用 SELECT *");
        }

        // 7. LIMIT 规范化
        LimitResult limitResult = enforceLimit(sql, maxRows);
        if (!limitResult.valid()) {
            return ValidationResult.invalid(limitResult.error());
        }

        return ValidationResult.valid(limitResult.sql(), tables, aliasByTable);
    }

    /**
     * 判断是否存在最外层裸通配符（{@code SELECT *} / {@code t.*}）。
     * <p>只检查括号层级 0 的 {@code *}，因此 {@code COUNT(*)}、{@code ROUND(x * 100.0, 1)}
     * 这类函数内用法不会被误判。</p>
     */
    private static boolean hasTopLevelWildcard(String masked) {
        int[] depth = SqlTextScanner.depthArray(masked);
        for (int i = 0; i < masked.length(); i++) {
            if (masked.charAt(i) == '*' && depth[i] == 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断是否存在"最外层逗号连接"（形如 {@code FROM a, b}）。
     * <p>做法：定位第一个最外层 FROM，取其到下一个子句关键字（WHERE/ON/GROUP/ORDER/LIMIT/HAVING/JOIN…）
     * 之间的区间，若该区间内出现最外层逗号，即视为逗号连接。</p>
     */
    private static boolean hasTopLevelCommaJoin(String masked) {
        List<SqlTextScanner.KeywordHit> fromHits = SqlTextScanner.topLevelHits(masked, "FROM");
        if (fromHits.isEmpty()) {
            return false;
        }
        int fromStart = fromHits.get(0).end();
        int end = masked.length();
        for (SqlTextScanner.KeywordHit hit : SqlTextScanner.topLevelHits(
                masked, "WHERE", "GROUP", "ORDER", "LIMIT", "HAVING", "OFFSET",
                "ON", "USING", "JOIN", "LEFT", "RIGHT", "INNER", "CROSS", "STRAIGHT_JOIN", "NATURAL")) {
            if (hit.start() > fromStart) {
                end = hit.start();
                break;
            }
        }
        int[] depth = SqlTextScanner.depthArray(masked);
        for (int i = fromStart; i < end; i++) {
            if (masked.charAt(i) == ',' && depth[i] == 0) {
                return true;
            }
        }
        return false;
    }

    private static String stripQuotes(String identifier) {
        if (identifier == null) {
            return "";
        }
        String value = identifier.trim();
        if ((value.startsWith("`") && value.endsWith("`") && value.length() >= 2)
                || (value.startsWith("[") && value.endsWith("]") && value.length() >= 2)) {
            value = value.substring(1, value.length() - 1);
        }
        return value;
    }

    /** 强制 LIMIT：缺失则追加，超限则收紧，无法解析则拒绝。 */
    private static LimitResult enforceLimit(String sql, int maxRows) {
        int safeMax = Math.max(1, maxRows);
        String masked = SqlTextScanner.maskLiterals(sql);
        List<SqlTextScanner.KeywordHit> limitHits = SqlTextScanner.topLevelHits(masked, "LIMIT");
        if (limitHits.isEmpty()) {
            // 用换行分隔，避免 SQL 末尾存在行注释时把追加的 LIMIT 一并注释掉
            return LimitResult.ok(sql + "\nLIMIT " + safeMax);
        }
        SqlTextScanner.KeywordHit hit = limitHits.get(limitHits.size() - 1);
        String tail = masked.substring(hit.end());

        Matcher simple = LIMIT_SIMPLE.matcher(tail);
        Matcher offsetForm = LIMIT_OFFSET.matcher(tail);
        int countGroupStart;
        int countGroupEnd;
        String countText;
        if (offsetForm.lookingAt()) {
            countGroupStart = hit.end() + offsetForm.start(1);
            countGroupEnd = hit.end() + offsetForm.end(1);
            countText = offsetForm.group(1);
        } else if (simple.lookingAt()) {
            if (simple.group(2) != null) {
                countGroupStart = hit.end() + simple.start(2);
                countGroupEnd = hit.end() + simple.end(2);
                countText = simple.group(2);
            } else {
                countGroupStart = hit.end() + simple.start(1);
                countGroupEnd = hit.end() + simple.end(1);
                countText = simple.group(1);
            }
        } else {
            return LimitResult.error("LIMIT 子句无法解析，出于安全考虑拒绝执行");
        }

        int count;
        try {
            count = Integer.parseInt(countText);
        } catch (NumberFormatException e) {
            return LimitResult.error("LIMIT 数值非法");
        }
        if (count <= safeMax) {
            return LimitResult.ok(sql);
        }
        String rewritten = sql.substring(0, countGroupStart) + safeMax + sql.substring(countGroupEnd);
        return LimitResult.ok(rewritten);
    }

    /**
     * 校验结果。
     *
     * @param valid        是否通过
     * @param sql          规范化后的 SQL（含 LIMIT）
     * @param errorMessage 失败原因
     * @param tables       引用的表（小写）
     * @param aliasByTable 表 → 别名
     */
    public record ValidationResult(
            boolean valid,
            String sql,
            String errorMessage,
            Set<String> tables,
            Map<String, String> aliasByTable
    ) {
        static ValidationResult invalid(String message) {
            return new ValidationResult(false, null, message, Set.of(), Map.of());
        }

        static ValidationResult valid(String sql, Set<String> tables, Map<String, String> aliasByTable) {
            return new ValidationResult(true, sql, null, tables, aliasByTable);
        }
    }

    private record LimitResult(boolean valid, String sql, String error) {
        static LimitResult ok(String sql) {
            return new LimitResult(true, sql, null);
        }

        static LimitResult error(String message) {
            return new LimitResult(false, null, message);
        }
    }

    /** 供外部复用的列名安全判断（结果集过滤用）。 */
    public static boolean isSensitiveColumn(String column, List<String> deniedKeywords) {
        if (column == null || deniedKeywords == null) {
            return false;
        }
        String lower = column.toLowerCase(Locale.ROOT);
        List<String> keywords = new ArrayList<>(deniedKeywords);
        return keywords.stream()
                .filter(k -> k != null && !k.isBlank())
                .map(k -> k.toLowerCase(Locale.ROOT))
                .anyMatch(lower::contains);
    }
}
