package com.demand.system.module.nl2sql.support;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 行级数据权限 / 软删除过滤注入的回归测试。
 *
 * <p>核心不变量：模型写的过滤条件<b>不可信</b>。服务端必须无条件注入
 * {@code deleted_at = 0} 与非超管的 {@code org_id IN (...)}，且必须把模型原有的
 * 条件整体加括号 —— 否则 {@code ... OR 1=1} 会因 {@code AND} 优先级高于 {@code OR}
 * 而让组织隔离形同虚设。</p>
 */
class SqlScopeInjectorTest {

    private static final Set<String> SOFT_DELETE = Set.of("requirements", "projects", "users", "sys_org");
    private static final Set<String> ORG_SCOPED = Set.of("requirements", "users", "sys_org");

    @Test
    void buildsSoftDeleteAndOrgPredicatesForNonSuperAdmin() {
        List<String> predicates = SqlScopeInjector.buildPredicates(
                Set.of("requirements"), Map.of("requirements", "r"),
                SOFT_DELETE, ORG_SCOPED, List.of(1L, 10L), false);

        assertEquals(List.of("r.deleted_at = 0", "r.org_id IN (1, 10)"), predicates);
    }

    @Test
    void superAdminOnlyGetsSoftDeletePredicate() {
        List<String> predicates = SqlScopeInjector.buildPredicates(
                Set.of("requirements"), Map.of("requirements", "r"),
                SOFT_DELETE, ORG_SCOPED, List.of(1L, 10L), true);

        assertEquals(List.of("r.deleted_at = 0"), predicates);
    }

    @Test
    void noOrgPredicateWhenVisibleOrgIdsIsEmpty() {
        List<String> predicates = SqlScopeInjector.buildPredicates(
                Set.of("requirements"), Map.of("requirements", "r"),
                SOFT_DELETE, ORG_SCOPED, List.of(), false);

        assertEquals(List.of("r.deleted_at = 0"), predicates);
    }

    @Test
    void fallsBackToTableNameWhenAliasIsAbsent() {
        List<String> predicates = SqlScopeInjector.buildPredicates(
                Set.of("requirements"), Map.of(),
                SOFT_DELETE, ORG_SCOPED, List.of(7L), false);

        assertEquals(List.of("requirements.deleted_at = 0", "requirements.org_id IN (7)"), predicates);
    }

    @Test
    void emitsOnePredicatePerJoinedTable() {
        List<String> predicates = SqlScopeInjector.buildPredicates(
                Set.of("requirements", "projects"), Map.of("requirements", "r", "projects", "p"),
                SOFT_DELETE, ORG_SCOPED, List.of(1L), false);

        assertEquals(3, predicates.size(), predicates.toString());
        assertTrue(predicates.containsAll(List.of(
                "r.deleted_at = 0", "p.deleted_at = 0", "r.org_id IN (1)")), predicates.toString());
    }

    @Test
    void skipsTablesWithoutSoftDeleteOrOrgColumn() {
        // reviews 既没有 deleted_at 也没有 org_id，不应产生任何谓词
        List<String> predicates = SqlScopeInjector.buildPredicates(
                Set.of("reviews"), Map.of("reviews", "rv"),
                SOFT_DELETE, ORG_SCOPED, List.of(1L), false);

        assertTrue(predicates.isEmpty(), predicates.toString());
    }

    @Test
    void emptyPredicatesLeaveSqlUntouched() {
        String sql = "SELECT id FROM requirements LIMIT 200";
        assertSame(sql, SqlScopeInjector.inject(sql, List.of()));
        assertSame(sql, SqlScopeInjector.inject(sql, null));
    }

    @Test
    void wrapsModelConditionsInParenthesesToStopOrPrecedenceLeak() {
        String sql = "SELECT r.status, COUNT(*) AS cnt FROM requirements r "
                + "WHERE r.status = '新建' OR r.status = '开发中' GROUP BY r.status LIMIT 200";

        String injected = SqlScopeInjector.inject(sql, List.of("r.deleted_at = 0", "r.org_id IN (1)"));

        assertTrue(injected.contains(
                "WHERE (r.deleted_at = 0 AND r.org_id IN (1)) AND (r.status = '新建' OR r.status = '开发中')"),
                injected);
    }

    @Test
    void insertsWhereBeforeGroupByWhenModelProvidedNoWhere() {
        String sql = "SELECT r.status, COUNT(*) AS cnt FROM requirements r GROUP BY r.status LIMIT 200";

        String injected = SqlScopeInjector.inject(sql, List.of("r.deleted_at = 0"));

        assertTrue(injected.contains("FROM requirements r WHERE (r.deleted_at = 0) GROUP BY r.status"), injected);
        assertFalse(injected.contains("  WHERE"), "不应产生双空格：" + injected);
    }

    @Test
    void insertsWhereBeforeLimitWhenThereIsNoGroupBy() {
        String sql = "SELECT COUNT(*) AS total FROM requirements r\nLIMIT 200";

        String injected = SqlScopeInjector.inject(sql, List.of("r.deleted_at = 0"));

        assertTrue(injected.contains("FROM requirements r WHERE (r.deleted_at = 0) LIMIT 200"), injected);
    }

    @Test
    void ignoresWhereInsideSubExpressionWhenInjecting() {
        // WHERE 只认最外层：字符串字面量里的 "WHERE" 不能被当成注入点
        String sql = "SELECT id FROM requirements r WHERE r.title = 'WHERE 1=1' LIMIT 200";

        String injected = SqlScopeInjector.inject(sql, List.of("r.deleted_at = 0"));

        assertTrue(injected.contains("WHERE (r.deleted_at = 0) AND (r.title = 'WHERE 1=1')"), injected);
    }
}
