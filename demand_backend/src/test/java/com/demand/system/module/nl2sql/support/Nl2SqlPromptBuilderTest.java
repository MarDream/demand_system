package com.demand.system.module.nl2sql.support;

import com.demand.system.module.nl2sql.schema.Nl2SqlSemantics;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 提示词构造器的契约测试。
 *
 * <p>提示词是"代码"的一种——改错了不会编译失败，只会让模型行为悄悄退化。
 * 这里守住两条容易改坏、且已经踩过的线：</p>
 * <ol>
 *   <li>schema 与全部 few-shot 必须真的被拼进 SQL 生成提示词（少了示例准确率会掉）；</li>
 *   <li>回答生成提示词必须禁止臆造表名——曾出现结果为空时，模型在"建议"里
 *       凭空推荐去查一张并不存在的 {@code requirement_attachments} 表。</li>
 * </ol>
 */
class Nl2SqlPromptBuilderTest {

    @Test
    void sqlSystemPromptCarriesSchemaRulesAndEveryFewShot() {
        String prompt = Nl2SqlPromptBuilder.buildSqlSystemPrompt("SCHEMA_MARKER_XYZ", true, 200);

        assertTrue(prompt.contains("SCHEMA_MARKER_XYZ"), "schema 描述必须被拼进提示词");
        assertTrue(prompt.contains("200"), "最大行数上限必须出现在提示词里");
        assertTrue(prompt.contains("禁止臆造表名或列名"), "必须明确禁止臆造表名/列名");
        assertTrue(prompt.contains("禁止子查询"), "必须保留「平面 SELECT」约束");

        for (Nl2SqlSemantics.FewShot shot : Nl2SqlSemantics.fewShots()) {
            assertTrue(prompt.contains(shot.question()),
                    "few-shot 问题未出现在提示词里：" + shot.question());
            assertTrue(prompt.contains(shot.sql()),
                    "few-shot SQL 未出现在提示词里：" + shot.question());
        }
    }

    @Test
    void sqlSystemPromptTellsNonAdminThatScopeIsInjectedServerSide() {
        String nonAdmin = Nl2SqlPromptBuilder.buildSqlSystemPrompt("S", false, 100);
        assertTrue(nonAdmin.contains("普通用户"), "普通用户提示词必须说明数据权限边界");
        assertTrue(nonAdmin.contains("不要") && nonAdmin.contains("org_id"),
                "必须明确告知普通用户不要自己写 org_id 条件（由后端强制注入）：\n" + nonAdmin);

        String admin = Nl2SqlPromptBuilder.buildSqlSystemPrompt("S", true, 100);
        assertTrue(admin.contains("超级管理员"));
    }

    /**
     * 结果为空时，回答提示词必须把"没有数据"这个事实明确传给模型，
     * 否则模型容易自行脑补原因（包括编造表名）。
     */
    @Test
    void answerUserPromptMarksEmptyResultExplicitly() {
        String prompt = Nl2SqlPromptBuilder.buildAnswerUserPrompt(
                "哪些工单上传了附件", null, 20, false, false);
        assertTrue(prompt.contains("结果行数：0"), "空结果必须显式给出 0 行：\n" + prompt);
        assertTrue(prompt.contains("[]（无数据）"), "空结果必须标记为无数据：\n" + prompt);
    }

    @Test
    void answerSystemPromptForbidsInventingTableNames() {
        String prompt = Nl2SqlPromptBuilder.buildAnswerSystemPrompt(true);
        assertTrue(prompt.contains("禁止臆造不存在的表"),
                "回答提示词必须禁止臆造表名（曾幻觉出不存在的 requirement_attachments 表）：\n" + prompt);
        assertTrue(prompt.contains("已执行的 SQL"),
                "必须把可引用的表/列限定在已执行的 SQL 之内：\n" + prompt);
        assertFalse(prompt.contains("并建议如何调整查询条件"),
                "旧的「建议如何调整查询条件」措辞会诱导模型自由发挥，应已移除：\n" + prompt);
    }

    /**
     * 含英文枚举编码的取数问题不得被判成"非取数"。
     *
     * <p>踩过的坑：问「状态是 PENDING_CONFIRM 的需求有哪些？列出编号和标题」，
     * 同一个问题连问 3 次，有 1 次被判定 {@code needDatabase=false}，
     * 助手静默退回"操作导航"给出点击步骤，用户一条数据都拿不到；
     * 其余取数问题 3/3 稳定。触发因素就是问题里那个模型不认识的英文编码。</p>
     *
     * <p>修法是在提示词里明确：出现字段名/英文枚举编码不影响取数判定，
     * 并且拿不准时优先取数（用空结果回答，而不是退回导航）。</p>
     */
    @Test
    void sqlSystemPromptKeepsEnumCodeQuestionsOnTheDataPath() {
        String prompt = Nl2SqlPromptBuilder.buildSqlSystemPrompt("S", true, 100);
        assertTrue(prompt.contains("英文枚举编码"),
                "提示词必须说明字段名/英文枚举编码不影响取数判定：\n" + prompt);
        assertTrue(prompt.contains("优先取数"),
                "提示词必须给出「拿不准优先取数」的兜底，避免静默退回操作导航：\n" + prompt);
        assertTrue(prompt.contains("\"needDatabase\": false"),
                "提示词必须保留 needDatabase=false 的适用场景（纯系统用法问题）：\n" + prompt);
    }
}
