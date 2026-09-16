package com.demand.system.module.nl2sql.support;

import java.util.List;
import java.util.Locale;

/**
 * 数据查询意图的轻量规则识别。
 *
 * <p>作用有两个：</p>
 * <ol>
 *   <li>在"自动路由"场景下，低成本判断用户问题是否值得走 NL2SQL（避免每次提问都调用模型分类）；</li>
 *   <li>命中明显的"操作指导类"问句时直接排除，防止把"如何新建需求"误判为数据查询。</li>
 * </ol>
 *
 * <p>最终判定仍以模型输出的 {@code needDatabase} 为准（为 false 时助手会自动回退到操作导航），
 * 本类只是前置闸门，宁可漏判也不要错判导致"操作问题被当成取数问题"。</p>
 */
public final class Nl2SqlIntentDetector {

    private Nl2SqlIntentDetector() {
    }

    /**
     * 强数据信号词：语义上几乎只可能出现在"取数/统计"问题里。
     * 命中即判定为数据查询，即使同时出现"如何/怎么"等疑问词。
     */
    private static final List<String> STRONG_DATA_KEYWORDS = List.of(
            "多少", "几个", "几条", "数量", "总数", "合计", "统计", "汇总", "分布", "占比",
            "百分比", "排名", "排行", "前几", "趋势", "环比", "同比", "平均值", "平均",
            "逾期", "超期", "top", "报表"
    );

    /**
     * 弱数据信号词：出现时倾向数据查询，但若同时命中操作指导词则让位。
     */
    private static final List<String> WEAK_DATA_KEYWORDS = List.of(
            "比例", "增长", "下降", "最大", "最小", "最多", "最少", "列出", "列表", "哪些",
            "分别", "各", "每月", "每天", "每周", "本周", "本月", "上个月", "上月", "最近",
            "过去", "超过", "未完成", "已完成", "数据", "看板", "维度", "分组", "查一下"
    );

    /** 操作指导信号：命中且没有强数据信号时，判定为非数据查询。 */
    private static final List<String> GUIDE_KEYWORDS = List.of(
            "如何", "怎么", "怎样", "在哪里", "在哪", "入口", "步骤", "操作流程", "教我",
            "为什么", "报错", "失败原因", "权限不足", "怎么配", "如何配", "怎么设置",
            "什么意思", "是什么"
    );

    /**
     * 判断问题是否"很像"数据查询。
     *
     * @param question 用户问题
     * @return true 表示值得尝试 NL2SQL
     */
    public static boolean looksLikeDataQuery(String question) {
        if (question == null || question.isBlank()) {
            return false;
        }
        String normalized = question.toLowerCase(Locale.ROOT);

        // 1. 强信号直接判定为数据查询（"统计一下有多少需求" 这类同时含疑问词的问题也应命中）
        if (STRONG_DATA_KEYWORDS.stream().anyMatch(normalized::contains)) {
            return true;
        }
        // 2. 命中操作指导词则排除（"如何按状态筛选需求" 不应走取数）
        if (GUIDE_KEYWORDS.stream().anyMatch(normalized::contains)) {
            return false;
        }
        // 3. 弱信号兜底
        return WEAK_DATA_KEYWORDS.stream().anyMatch(normalized::contains);
    }
}
