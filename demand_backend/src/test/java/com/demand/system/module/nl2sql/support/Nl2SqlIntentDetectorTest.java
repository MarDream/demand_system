package com.demand.system.module.nl2sql.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 数据查询意图前置闸门测试。
 *
 * <p>这道闸门只负责"低成本粗筛"：宁可漏判（走通用问答，用户再点一下数据模式），
 * 也不要错判 —— 把"如何新建需求"当成取数问题会让助手答非所问。</p>
 */
class Nl2SqlIntentDetectorTest {

    private static void assertDataQuery(String question) {
        assertTrue(Nl2SqlIntentDetector.looksLikeDataQuery(question), "应判定为数据查询：" + question);
    }

    private static void assertNotDataQuery(String question) {
        assertFalse(Nl2SqlIntentDetector.looksLikeDataQuery(question), "不应判定为数据查询：" + question);
    }

    @Test
    void blankInputIsNotADataQuery() {
        assertNotDataQuery(null);
        assertNotDataQuery("");
        assertNotDataQuery("   ");
    }

    @Test
    void strongSignalWordsAlwaysWin() {
        assertDataQuery("一共有多少个需求？");
        assertDataQuery("各个状态的需求数量分布");
        assertDataQuery("按优先级统计需求数量");
        assertDataQuery("需求总数是多少");
        assertDataQuery("各项目的需求占比");
        assertDataQuery("需求最多的前5个负责人排名");
        assertDataQuery("最近30天新增需求趋势");
        assertDataQuery("本月和上月环比如何");
        assertDataQuery("哪些需求逾期了");
    }

    @Test
    void strongSignalBeatsGuideWords() {
        // 同时出现"如何/多少"时，取数意图更明确，不应被操作指导词挡掉
        assertDataQuery("统计一下有多少个需求");
        assertDataQuery("如何查看各状态的占比");
    }

    @Test
    void guideQuestionsAreExcluded() {
        assertNotDataQuery("如何按状态筛选需求");
        assertNotDataQuery("怎么新建一个需求");
        assertNotDataQuery("需求详情的入口在哪里");
        assertNotDataQuery("为什么提交需求时报错");
        assertNotDataQuery("工作流配置是什么意思");
        assertNotDataQuery("教我配置评审流程");
    }

    @Test
    void weakSignalWordsAreEnoughOnTheirOwn() {
        assertDataQuery("需求列表");
        assertDataQuery("最近有哪些需求");
        assertDataQuery("各项目的需求情况");
        assertDataQuery("未完成的需求");
    }

    @Test
    void guideWordsBeatWeakSignalWords() {
        // 弱信号 + 操作指导 → 判定为操作指导，避免"怎么查数据"被当成取数
        assertNotDataQuery("怎么查看需求列表");
        assertNotDataQuery("如何导出各项目数据");
    }
}
