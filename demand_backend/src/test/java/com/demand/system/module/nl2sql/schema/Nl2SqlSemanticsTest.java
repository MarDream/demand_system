package com.demand.system.module.nl2sql.schema;

import com.demand.system.module.nl2sql.support.SqlSafetyValidator;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 语义库（提示词素材）的一致性测试。
 *
 * <p>这里守的是两个"踩过的坑"：</p>
 * <ol>
 *   <li><b>枚举值必须与真实数据一致。</b>数据库列注释是过期的
 *       （{@code requirements.priority} 注释写 critical/high/medium/low，实际存 P0~P3），
 *       一旦提示词里写死 {@code RELEASED}/{@code CLOSED}，模型就会生成永远查不到数据的条件。
 *       因此这些取值改为运行时从字典表读取，本测试防止有人再把它们硬编码回来。</li>
 *   <li><b>few-shot 示例必须自己先通过安全校验。</b>示例会被模型直接模仿，
 *       如果示例本身违反"平面 SELECT / 白名单"约束，等于在教模型违规。</li>
 * </ol>
 */
class Nl2SqlSemanticsTest {

    private static final Set<String> ALLOWED = Nl2SqlSemantics.defaultAllowedTables().stream()
            .map(t -> t.toLowerCase(Locale.ROOT))
            .collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll);

    private static final List<String> DENIED = List.of(
            "password", "passwd", "secret", "api_key", "apikey", "access_key",
            "private_key", "token", "credential", "salt", "signature");

    @Test
    void everyFewShotExamplePassesOurOwnSafetyValidator() {
        for (Nl2SqlSemantics.FewShot shot : Nl2SqlSemantics.fewShots()) {
            SqlSafetyValidator.ValidationResult result =
                    SqlSafetyValidator.validate(shot.sql(), ALLOWED, 200, DENIED);
            assertTrue(result.valid(),
                    "示例 SQL 未通过安全校验（会被模型模仿）：" + shot.question() + " → " + result.errorMessage());
        }
    }

    @Test
    void fewShotSqlOnlyTouchesWhitelistedTables() {
        for (Nl2SqlSemantics.FewShot shot : Nl2SqlSemantics.fewShots()) {
            SqlSafetyValidator.ValidationResult result =
                    SqlSafetyValidator.validate(shot.sql(), ALLOWED, 200, DENIED);
            assertTrue(ALLOWED.containsAll(result.tables()),
                    "示例引用了白名单外的表：" + result.tables());
        }
    }

    @Test
    void businessRulesDoNotHardcodeStaleEnumValues() {
        String rules = String.join("\n", Nl2SqlSemantics.businessRules());
        assertFalse(rules.contains("RELEASED"), "不应再出现不存在的状态值 RELEASED：\n" + rules);
        assertFalse(rules.contains("CLOSED"), "不应再出现不存在的状态值 CLOSED：\n" + rules);
        assertFalse(rules.contains("critical"), "优先级取值应来自字典表，不应硬编码 critical：\n" + rules);
    }

    @Test
    void fewShotsDoNotHardcodeStaleEnumValues() {
        for (Nl2SqlSemantics.FewShot shot : Nl2SqlSemantics.fewShots()) {
            assertFalse(shot.sql().contains("RELEASED"), "示例中不应出现 RELEASED：" + shot.sql());
            assertFalse(shot.sql().contains("CLOSED"), "示例中不应出现 CLOSED：" + shot.sql());
        }
    }

    @Test
    void reviewResultMeaningMatchesColumnComment() {
        assertTrue(Nl2SqlSemantics.reviewResultMeaning().keySet().containsAll(List.of("pass", "reject", "pending")));
    }

    /**
     * 白名单绝不能包含存放凭据/密钥的表。
     *
     * <p>这些表在库里确实存在且含 token / secret / api_key / credential 列，
     * 一旦被加进白名单就等于把密钥暴露给自然语言查询。</p>
     */
    @Test
    void whitelistExcludesCredentialBearingTables() {
        List<String> forbidden = List.of(
                "llm_providers", "llm_models", "llm_applications",
                "git_platforms", "bitable_form_publishes", "bitable_view_shares",
                "bitable_webhook_subscriptions", "knowledge_document_shares",
                "assistant_messages", "question_logs", "sys_users", "sys_permissions"
        );
        for (String table : forbidden) {
            assertFalse(ALLOWED.contains(table), "白名单不应包含凭据/内部表：" + table);
        }
    }

    @Test
    void whitelistTablesAreLowerCaseAndUnique() {
        List<String> raw = Nl2SqlSemantics.defaultAllowedTables();
        assertTrue(raw.stream().noneMatch(t -> !t.equals(t.toLowerCase(Locale.ROOT))),
                "白名单表名必须是小写：" + raw);
        assertTrue(raw.size() == ALLOWED.size(), "白名单存在重复项：" + raw);
    }

    /**
     * “有附件”必须用 {@code JSON_LENGTH} 判定，不能只写 {@code IS NOT NULL}。
     *
     * <p>踩过的坑：问「哪些工单上传了附件」，模型生成了
     * {@code r.attachments IS NOT NULL}；而空数组 {@code []} 同样满足 {@code IS NOT NULL}，
     * 于是把全部工单都查了出来，结果卡片里每行 attachments 都是 {@code []}，
     * 标题却写着“已上传附件的需求列表”。</p>
     */
    @Test
    void attachmentFewShotTeachesJsonLengthInsteadOfIsNotNull() {
        Nl2SqlSemantics.FewShot shot = Nl2SqlSemantics.fewShots().stream()
                .filter(s -> s.question().contains("附件"))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "应有「有附件」类的 few-shot 示例，用来纠正 JSON 数组的判空口径"));
        assertTrue(shot.sql().contains("JSON_LENGTH("),
                "附件类示例必须用 JSON_LENGTH 判空，否则模型会模仿成 IS NOT NULL：" + shot.sql());
        assertTrue(shot.sql().contains("> 0"),
                "附件类示例必须显式排除空数组：" + shot.sql());
    }

    @Test
    void businessRulesExplainJsonArrayEmptiness() {
        String rules = String.join("\n", Nl2SqlSemantics.businessRules());
        assertTrue(rules.contains("JSON_LENGTH"),
                "业务口径规则必须说明 JSON 数组列如何判“有内容”：\n" + rules);
        assertTrue(rules.contains("空数组") || rules.contains("[]"),
                "业务口径规则必须点明空数组 [] 不算有内容：\n" + rules);
    }

    /**
     * JSON 列提示必须同时点明"用 JSON_LENGTH"和"[] 不算有内容"，
     * 且类型识别要能认住 information_schema 里的 {@code json} / {@code json DEFAULT NULL} 形态。
     */
    @Test
    void jsonColumnHintStatesTheRuleAndTypeDetectionWorks() {
        String hint = Nl2SqlSemantics.jsonColumnHint();
        assertTrue(hint.contains("JSON_LENGTH"), "JSON 列提示必须给出 JSON_LENGTH 口径：" + hint);
        assertTrue(hint.contains("[]"), "JSON 列提示必须点明空数组不算有内容：" + hint);

        assertTrue(Nl2SqlSemantics.isJsonColumnType("json"));
        assertTrue(Nl2SqlSemantics.isJsonColumnType("JSON"));
        assertFalse(Nl2SqlSemantics.isJsonColumnType("varchar(255)"));
        assertFalse(Nl2SqlSemantics.isJsonColumnType("bigint"));
        assertFalse(Nl2SqlSemantics.isJsonColumnType(null));
    }

    /**
     * {@code node_status} 必须给出"存英文编码、中文名在 status 列"的口径。
     *
     * <p>踩过的坑：{@code requirements.status} 存中文状态名（待确认），
     * {@code node_status} 存同一状态的英文编码（PENDING_CONFIRM），两列一一对应，
     * 但 {@code node_status} 的列注释只有"当前节点状态"四个字，看不出存的是编码。
     * 用户用编码提问时模型可能去比 {@code status}，条件恒不成立 → 静默返回空结果。</p>
     *
     * <p>同时这里守住"不许硬编码具体编码"：{@code workflow_states} 只有 name、没有 code，
     * 库里不存在权威的中英映射，写死任何编码都会随工作流配置过期。</p>
     */
    @Test
    void nodeStatusColumnHintExplainsWhichColumnHoldsWhat() {
        String hint = Nl2SqlSemantics.nodeStatusColumnHint();
        assertTrue(hint.contains("英文编码"),
                "node_status 提示必须点明该列存的是英文编码：" + hint);
        assertTrue(hint.contains("status"),
                "node_status 提示必须指出中文状态名在 status 列：" + hint);
        assertFalse(hint.contains("PENDING_CONFIRM") || hint.contains("IN_DEVELOPMENT"),
                "不应把具体工作流编码硬编码进提示（库里没有权威中英映射，会过期）：" + hint);
    }
}
