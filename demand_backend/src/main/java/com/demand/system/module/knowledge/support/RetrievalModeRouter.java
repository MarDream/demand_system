package com.demand.system.module.knowledge.support;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 智能检索路由：按问题特征自动选择检索模式，用户无需理解检索类型差异。
 *
 * 路由规则（可解释、无 LLM 依赖，单次正则判断开销可忽略）：
 * - keyword：问题带强精确匹配信号 —— 引号圈定的确定词、编号/代码型 token（REQ-123、A001、404 之类）、
 *   或不含疑问词的超短名词查询（如「报销标准」）。这类查询关键词命中即可精准定位。
 * - hybrid：其余全部默认走混合检索（向量召回 + 关键词召回 + RRF 融合 + 重排精排）。
 *   混合检索是语义/关键词的超集，配合重排序后质量最优，作为智能检索的兜底。
 * - semantic：不主动路由（混合检索已覆盖其能力），保留该模式仅供显式指定。
 */
public final class RetrievalModeRouter {

    /** 引号圈定的确定词：「报销标准」、"差旅费"、"审批流程" 等 */
    private static final Pattern QUOTED_TERM = Pattern.compile("[\"“”「」『』《》].{2,}?[\"“”「」『』》]");
    /** 编号/代码型 token：REQ-123、BUG_88、A001、404、2024Q3 等（字母开头+数字，或全大写+连字符） */
    private static final Pattern CODE_TOKEN = Pattern.compile(
            "\\b[A-Za-z]{1,10}-?\\d{2,}\\b|\\b[A-Z]{2,}[-_][A-Za-z0-9]{1,}\\b");
    /** 疑问/自然语言信号词：出现即倾向语义理解 */
    private static final Pattern QUESTIONISH = Pattern.compile(
            "如何|怎么|怎样|为什么|为什么|是什么|哪些|多少|是否|能否|可以吗|帮我|介绍|总结|对比|区别|吗[？?]|？|\\?|"
                    + "how|why|what|which|when|who|where", Pattern.CASE_INSENSITIVE);

    private RetrievalModeRouter() {
    }

    /**
     * 路由到实际检索模式。
     *
     * @param query           用户原始问题（或改写后的独立问题）
     * @param rewriteKeywords 查询改写产出的关键词，可为 null（仅影响扩展词，不影响路由结论）
     * @return "keyword" 或 "hybrid"
     */
    public static String route(String query, List<String> rewriteKeywords) {
        String q = query == null ? "" : query.trim();
        if (q.isEmpty()) {
            return "hybrid";
        }
        // 引号确定词 / 编号代码型查询 → 关键词检索精准命中
        if (QUOTED_TERM.matcher(q).find() || CODE_TOKEN.matcher(q).find()) {
            return "keyword";
        }
        // 超短名词查询且无疑问语气 → 关键词检索（如「报销标准」「考勤制度」）
        if (q.length() <= 6 && !QUESTIONISH.matcher(q).find()) {
            return "keyword";
        }
        return "hybrid";
    }

    /** 路由模式的中文说明，用于思维链/过程摘要展示 */
    public static String describe(String mode) {
        return switch (mode) {
            case "keyword" -> "关键词检索·编号与术语精准匹配";
            case "semantic" -> "语义检索·向量相似度匹配";
            case "hybrid" -> "混合检索·向量+关键词融合精排";
            default -> mode;
        };
    }
}
