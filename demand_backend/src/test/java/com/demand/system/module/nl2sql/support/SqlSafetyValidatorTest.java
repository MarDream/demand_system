package com.demand.system.module.nl2sql.support;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * NL2SQL SQL 安全校验的回归测试。
 *
 * <p>这是整个 NL2SQL 链路的安全底线：模型生成的 SQL 不可信，
 * 必须在执行前证明它"只读、单表平面、命中白名单、不碰敏感列、行数受限"。
 * 任何一个用例失败都意味着存在越权/写库/拖库风险，不要放宽断言。</p>
 */
class SqlSafetyValidatorTest {

    private static final Set<String> ALLOWED = Set.of(
            "requirements", "requirement_types", "priorities", "projects",
            "users", "sys_org", "iterations"
    );

    private static final List<String> DENIED = List.of(
            "password", "passwd", "secret", "api_key", "apikey", "token",
            "credential", "salt", "signature", "private_key"
    );

    private static final int MAX_ROWS = 200;

    private static SqlSafetyValidator.ValidationResult check(String sql) {
        return SqlSafetyValidator.validate(sql, ALLOWED, MAX_ROWS, DENIED);
    }

    private static void assertRejected(String sql) {
        SqlSafetyValidator.ValidationResult result = check(sql);
        assertFalse(result.valid(), "应当被拒绝但通过了校验：" + sql);
        assertNotNull(result.errorMessage(), "被拒绝时必须给出原因：" + sql);
        assertTrue(result.tables().isEmpty(), "被拒绝时不应返回表清单：" + sql);
    }

    // ---------------------------------------------------------------- 基本形态

    @Test
    void rejectsEmptyOrMissingSql() {
        assertRejected(null);
        assertRejected("");
        assertRejected("   ");
    }

    @Test
    void rejectsNonSelectStatements() {
        assertRejected("UPDATE requirements SET title = 'x' WHERE id = 1");
        assertRejected("DELETE FROM requirements WHERE id = 1");
        assertRejected("INSERT INTO requirements (title) VALUES ('x')");
        assertRejected("SHOW TABLES");
        assertRejected("DESC requirements");
    }

    @Test
    void rejectsMultipleStatements() {
        assertRejected("SELECT id FROM requirements; DROP TABLE users");
        assertRejected("SELECT id FROM requirements;SELECT id FROM users");
    }

    @Test
    void rejectsSubqueryUnionAndCte() {
        // 子查询：违反"平面 SELECT"约束，会导致权限注入无法覆盖全部数据来源
        assertRejected("SELECT id FROM requirements WHERE id IN (SELECT id FROM users)");
        assertRejected("SELECT id FROM requirements UNION SELECT id FROM users");
        assertRejected("WITH t AS (SELECT id FROM requirements) SELECT id FROM t");
        assertRejected("SELECT (SELECT COUNT(*) FROM users) AS c FROM requirements");
    }

    // ---------------------------------------------------------------- 关键字黑名单

    @Test
    void rejectsWriteAndDdlKeywords() {
        assertRejected("SELECT id INTO OUTFILE '/tmp/x' FROM requirements");
        assertRejected("SELECT id FROM requirements LOCK IN SHARE MODE");
        assertRejected("SELECT id FROM requirements FOR UPDATE");
        assertRejected("SELECT id FROM requirements PROCEDURE ANALYSE()");
        assertRejected("SELECT id FROM requirements WHERE id = 1 UNION ALL SELECT id FROM users");
    }

    @Test
    void rejectsDangerousFunctions() {
        assertRejected("SELECT SLEEP(5) FROM requirements");
        assertRejected("SELECT BENCHMARK(10000000, MD5('a')) FROM requirements");
        assertRejected("SELECT LOAD_FILE('/etc/passwd') FROM requirements");
        assertRejected("SELECT @@version FROM requirements");
        assertRejected("SELECT @@global.max_connections FROM requirements");
    }

    @Test
    void rejectsCrossDatabaseAndSystemSchemas() {
        assertRejected("SELECT id FROM information_schema.tables");
        assertRejected("SELECT id FROM mysql.user");
        assertRejected("SELECT id FROM performance_schema.session_variables");
        assertRejected("SELECT id FROM sys.sys_config");
        assertRejected("SELECT id FROM otherdb.requirements");
    }

    @Test
    void rejectsImplicitCommaJoin() {
        // 逗号连接的表不会被白名单与权限注入覆盖，必须显式 JOIN ... ON
        assertRejected("SELECT r.id FROM requirements r, users u WHERE r.id = u.id");
        assertRejected("SELECT r.id FROM requirements r, users u");
    }

    // ---------------------------------------------------------------- 白名单

    @Test
    void rejectsTablesOutsideWhitelist() {
        assertRejected("SELECT id FROM sys_permissions");
        assertRejected("SELECT id FROM assistant_messages");
        assertRejected("SELECT id FROM llm_applications");
    }

    @Test
    void reportsWhitelistViolationBeforeWildcardViolation() {
        // 错误信息应指向真正的首要问题（表越界），而不是笼统的 SELECT *
        SqlSafetyValidator.ValidationResult result = check("SELECT * FROM sys_permissions");
        assertFalse(result.valid());
        assertTrue(result.errorMessage().contains("不在允许查询范围"),
                "错误信息应指向表白名单越界，实际为：" + result.errorMessage());
    }

    // ---------------------------------------------------------------- 敏感列

    @Test
    void rejectsSensitiveColumns() {
        assertRejected("SELECT u.password FROM users u");
        assertRejected("SELECT u.password_hash FROM users u");
        assertRejected("SELECT u.api_key FROM users u");
        assertRejected("SELECT u.access_token FROM users u");
        assertRejected("SELECT salt FROM users");
        assertRejected("SELECT private_key FROM users");
    }

    @Test
    void rejectsSensitiveColumnsEvenWhenBacktickQuoted() {
        // 反引号是标识符引用，不能成为绕过敏感列检查的通道
        assertRejected("SELECT u.`password` FROM users u");
        assertRejected("SELECT `password` FROM users");
        assertRejected("SELECT u.`api_key` FROM users u");
    }

    @Test
    void allowsOrdinaryColumnsThatMerelyContainKeywordSubstrings() {
        // 词边界匹配：user_id 不应被 USER 关键字误伤，token_count 不属于敏感列
        SqlSafetyValidator.ValidationResult result =
                check("SELECT u.user_id, u.user_name, u.status FROM users u");
        assertTrue(result.valid(), "普通列不应被拒绝：" + result.errorMessage());
    }

    @Test
    void isSensitiveColumnMatchesCaseInsensitively() {
        assertTrue(SqlSafetyValidator.isSensitiveColumn("password", DENIED));
        assertTrue(SqlSafetyValidator.isSensitiveColumn("PASSWORD_HASH", DENIED));
        assertTrue(SqlSafetyValidator.isSensitiveColumn("user_api_key", DENIED));
        assertFalse(SqlSafetyValidator.isSensitiveColumn("id", DENIED));
        assertFalse(SqlSafetyValidator.isSensitiveColumn("status", DENIED));
        assertFalse(SqlSafetyValidator.isSensitiveColumn(null, DENIED));
    }

    // ---------------------------------------------------------------- 通配符

    @Test
    void rejectsTopLevelWildcard() {
        assertRejected("SELECT * FROM requirements");
        assertRejected("SELECT r.* FROM requirements r");
        assertRejected("SELECT *, r.id FROM requirements r");
    }

    @Test
    void allowsWildcardInsideAggregateFunctions() {
        SqlSafetyValidator.ValidationResult count = check("SELECT COUNT(*) AS total FROM requirements");
        assertTrue(count.valid(), "COUNT(*) 不应被误判：" + count.errorMessage());

        SqlSafetyValidator.ValidationResult ratio = check(
                "SELECT ROUND(SUM(CASE WHEN status = 2 THEN 1 ELSE 0 END) * 100.0 / COUNT(*), 1) AS rate "
                        + "FROM requirements");
        assertTrue(ratio.valid(), "表达式内乘法不应被误判：" + ratio.errorMessage());
    }

    // ---------------------------------------------------------------- 字面量与注释

    @Test
    void ignoresKeywordsHiddenInsideStringLiteralsAndComments() {
        SqlSafetyValidator.ValidationResult literal = check(
                "SELECT id FROM requirements WHERE title = 'DELETE FROM users'");
        assertTrue(literal.valid(), "字符串字面量中的关键字不应触发拦截：" + literal.errorMessage());

        SqlSafetyValidator.ValidationResult comment = check(
                "SELECT id FROM requirements /* DROP TABLE users */ WHERE id = 1");
        assertTrue(comment.valid(), "注释中的关键字不应触发拦截：" + comment.errorMessage());
    }

    @Test
    void keepsBacktickQuotedTableNamesResolvable() {
        SqlSafetyValidator.ValidationResult result = check("SELECT `id` FROM `requirements`");
        assertTrue(result.valid(), "反引号表名应能正常解析：" + result.errorMessage());
        assertTrue(result.tables().contains("requirements"));
    }

    // ---------------------------------------------------------------- 别名解析

    @Test
    void resolvesAliasesAndTreatsClauseKeywordsAsNonAliases() {
        SqlSafetyValidator.ValidationResult joined = check(
                "SELECT r.id, u.user_name FROM requirements r LEFT JOIN users u ON r.owner_id = u.id");
        assertTrue(joined.valid(), joined.errorMessage());
        assertEquals(Set.of("requirements", "users"), joined.tables());
        assertEquals("r", joined.aliasByTable().get("requirements"));
        assertEquals("u", joined.aliasByTable().get("users"));

        // 无别名时，紧跟其后的 WHERE 是子句关键字而非别名
        SqlSafetyValidator.ValidationResult plain = check(
                "SELECT id FROM requirements WHERE id = 1");
        assertTrue(plain.valid(), plain.errorMessage());
        assertEquals("requirements", plain.aliasByTable().get("requirements"));
    }

    @Test
    void allowsCommaInsideSelectList() {
        // 逗号连接被禁止，但 SELECT 列表中的逗号是合法的
        SqlSafetyValidator.ValidationResult result =
                check("SELECT r.id, r.title, p.name FROM requirements r JOIN projects p ON r.project_id = p.id");
        assertTrue(result.valid(), "SELECT 列表中的逗号不应被误判为逗号连接：" + result.errorMessage());
    }

    // ---------------------------------------------------------------- LIMIT 规范化

    @Test
    void appendsLimitWhenMissing() {
        SqlSafetyValidator.ValidationResult result = check("SELECT id FROM requirements");
        assertTrue(result.valid(), result.errorMessage());
        assertTrue(result.sql().toUpperCase().contains("LIMIT 200"), result.sql());
    }

    @Test
    void keepsLimitWhenWithinBound() {
        SqlSafetyValidator.ValidationResult result = check("SELECT id FROM requirements LIMIT 50");
        assertTrue(result.valid(), result.errorMessage());
        assertTrue(result.sql().contains("LIMIT 50"), result.sql());
        assertFalse(result.sql().contains("LIMIT 200"), "不应改写已合规的 LIMIT：" + result.sql());
    }

    @Test
    void tightensOversizedLimit() {
        SqlSafetyValidator.ValidationResult result = check("SELECT id FROM requirements LIMIT 5000");
        assertTrue(result.valid(), result.errorMessage());
        assertTrue(result.sql().contains("LIMIT 200"), result.sql());
        assertFalse(result.sql().contains("5000"), result.sql());
    }

    @Test
    void tightensOversizedLimitInOffsetForm() {
        SqlSafetyValidator.ValidationResult commaForm =
                check("SELECT id FROM requirements LIMIT 10, 5000");
        assertTrue(commaForm.valid(), commaForm.errorMessage());
        assertTrue(commaForm.sql().contains("LIMIT 10, 200"), commaForm.sql());

        SqlSafetyValidator.ValidationResult offsetForm =
                check("SELECT id FROM requirements LIMIT 5000 OFFSET 10");
        assertTrue(offsetForm.valid(), offsetForm.errorMessage());
        assertTrue(offsetForm.sql().contains("LIMIT 200 OFFSET 10"), offsetForm.sql());
    }

    @Test
    void keepsOffsetFormLimitWithinBound() {
        SqlSafetyValidator.ValidationResult result =
                check("SELECT id FROM requirements LIMIT 5 OFFSET 10");
        assertTrue(result.valid(), result.errorMessage());
        assertTrue(result.sql().contains("LIMIT 5 OFFSET 10"), result.sql());
    }

    @Test
    void rejectsUnparseableLimit() {
        assertRejected("SELECT id FROM requirements LIMIT abc");
        assertRejected("SELECT id FROM requirements LIMIT (SELECT 1)");
    }

    @Test
    void appendedLimitSurvivesTrailingLineComment() {
        SqlSafetyValidator.ValidationResult result =
                check("SELECT id FROM requirements WHERE id = 1 -- 只看一条");
        assertTrue(result.valid(), result.errorMessage());
        // LIMIT 必须独占一行，否则会被行注释吞掉
        assertTrue(result.sql().contains("\nLIMIT 200"), result.sql());
    }

    // ---------------------------------------------------------------- 真实取数形态

    @Test
    void acceptsRepresentativeRealWorldQueries() {
        String[] queries = {
                "SELECT r.status, COUNT(*) AS cnt FROM requirements r WHERE r.deleted_at = 0 "
                        + "GROUP BY r.status ORDER BY cnt DESC LIMIT 20",
                "SELECT p.name AS project, COUNT(r.id) AS total FROM requirements r "
                        + "JOIN projects p ON r.project_id = p.id GROUP BY p.name",
                "SELECT DATE_FORMAT(r.created_at, '%Y-%m') AS month, COUNT(*) AS cnt "
                        + "FROM requirements r GROUP BY month ORDER BY month",
                "SELECT u.user_name AS owner, COUNT(r.id) AS overdue FROM requirements r "
                        + "JOIN users u ON r.owner_id = u.id WHERE r.due_date < NOW() AND r.status != 3 "
                        + "GROUP BY u.user_name ORDER BY overdue DESC"
        };
        for (String sql : queries) {
            SqlSafetyValidator.ValidationResult result = check(sql);
            assertTrue(result.valid(), "真实取数语句不应被拒绝：" + sql + " → " + result.errorMessage());
        }
    }
}
