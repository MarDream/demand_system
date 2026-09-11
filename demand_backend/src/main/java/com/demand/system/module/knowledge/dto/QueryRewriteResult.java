package com.demand.system.module.knowledge.dto;

import java.util.List;

/**
 * 查询改写结果。
 *
 * @param standaloneQuery 可独立检索的完整问题（消解指代后）
 * @param keywords        关键词检索核心词
 * @param intent          意图标签（查询流程/查阅文档/统计信息/故障排查/需求查询/通用问答）
 * @param confidence      意图置信度
 * @param followUps       推荐追问问题
 * @param rewritten       是否真实执行了 LLM 改写（false 表示降级，各字段为兜底值）
 */
public record QueryRewriteResult(
        String standaloneQuery,
        List<String> keywords,
        String intent,
        double confidence,
        List<String> followUps,
        boolean rewritten
) {

    public static QueryRewriteResult fallback(String originalQuery) {
        List<String> keywords = java.util.Arrays.stream(
                        (originalQuery == null ? "" : originalQuery).split("[\\s,，。；;:：/\\\\|]+"))
                .map(String::trim)
                .filter(term -> term.length() >= 2)
                .distinct()
                .limit(5)
                .toList();
        return new QueryRewriteResult(originalQuery, keywords, "通用问答", 0.5, List.of(), false);
    }

    public String safeStandaloneQuery() {
        return standaloneQuery == null || standaloneQuery.isBlank() ? "" : standaloneQuery.trim();
    }
}
