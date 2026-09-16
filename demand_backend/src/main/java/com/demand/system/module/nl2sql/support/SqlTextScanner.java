package com.demand.system.module.nl2sql.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SQL 文本扫描工具。
 *
 * <p>安全校验的前提是"不被字符串字面量、标识符引号、注释干扰"。
 * 本类先把这些区域整体替换为空格（长度与换行保持不变），
 * 得到一份"骨架文本"，再在骨架上做关键字匹配与括号层级判断。</p>
 */
public final class SqlTextScanner {

    private SqlTextScanner() {
    }

    /**
     * 将字符串字面量、双引号标识符、行注释与块注释的内容替换为空格。
     * 保留原有长度与换行符，便于按位置映射回原 SQL。
     *
     * <p><b>反引号是例外</b>：MySQL 的反引号是"标识符引用"，其内容必须保留。
     * 若把反引号内容一并屏蔽，则 {@code SELECT `password` FROM users} 会绕过敏感列校验，
     * {@code FROM `requirements`} 也会因表名被抹掉而无法通过白名单解析。</p>
     */
    public static String maskLiterals(String sql) {
        if (sql == null || sql.isEmpty()) {
            return "";
        }
        char[] out = sql.toCharArray();
        int i = 0;
        int n = sql.length();
        while (i < n) {
            char c = sql.charAt(i);
            if (c == '`') {
                // 只屏蔽反引号定界符本身，保留其中的标识符文本
                out[i] = ' ';
                i++;
                while (i < n) {
                    char cur = sql.charAt(i);
                    if (cur == '`') {
                        if (i + 1 < n && sql.charAt(i + 1) == '`') {
                            // `` 为标识符内的转义，一并屏蔽定界符
                            out[i] = ' ';
                            out[i + 1] = ' ';
                            i += 2;
                            continue;
                        }
                        out[i] = ' ';
                        i++;
                        break;
                    }
                    i++;
                }
                continue;
            }
            if (c == '\'' || c == '"') {
                char quote = c;
                out[i] = ' ';
                i++;
                while (i < n) {
                    char cur = sql.charAt(i);
                    if (cur == '\\') {
                        // 转义字符：连同被转义字符一起屏蔽
                        out[i] = ' ';
                        if (i + 1 < n) {
                            out[i + 1] = ' ';
                        }
                        i += 2;
                        continue;
                    }
                    if (cur == quote) {
                        // 处理 '' / "" 形式的转义
                        if (i + 1 < n && sql.charAt(i + 1) == quote) {
                            out[i] = ' ';
                            out[i + 1] = ' ';
                            i += 2;
                            continue;
                        }
                        out[i] = ' ';
                        i++;
                        break;
                    }
                    out[i] = (cur == '\n' || cur == '\r') ? cur : ' ';
                    i++;
                }
                continue;
            }
            if (c == '#') {
                i = maskToLineEnd(sql, out, i);
                continue;
            }
            if (c == '-' && i + 1 < n && sql.charAt(i + 1) == '-') {
                i = maskToLineEnd(sql, out, i);
                continue;
            }
            if (c == '/' && i + 1 < n && sql.charAt(i + 1) == '*') {
                out[i] = ' ';
                out[i + 1] = ' ';
                i += 2;
                while (i < n) {
                    if (sql.charAt(i) == '*' && i + 1 < n && sql.charAt(i + 1) == '/') {
                        out[i] = ' ';
                        out[i + 1] = ' ';
                        i += 2;
                        break;
                    }
                    out[i] = (sql.charAt(i) == '\n' || sql.charAt(i) == '\r') ? sql.charAt(i) : ' ';
                    i++;
                }
                continue;
            }
            i++;
        }
        return new String(out);
    }

    private static int maskToLineEnd(String sql, char[] out, int start) {
        int i = start;
        while (i < sql.length() && sql.charAt(i) != '\n') {
            out[i] = ' ';
            i++;
        }
        return i;
    }

    /** 计算每个字符所处的括号层级（0 表示最外层）。 */
    public static int[] depthArray(String maskedSql) {
        int[] depth = new int[maskedSql.length() + 1];
        int d = 0;
        for (int i = 0; i < maskedSql.length(); i++) {
            char c = maskedSql.charAt(i);
            depth[i] = d;
            if (c == '(') {
                d++;
            } else if (c == ')') {
                d = Math.max(0, d - 1);
            }
        }
        depth[maskedSql.length()] = d;
        return depth;
    }

    /**
     * 查找关键字在"最外层"（括号层级 0）出现的位置。
     *
     * @param maskedSql 骨架文本
     * @param keywords  关键字（大小写不敏感）
     */
    public static List<KeywordHit> topLevelHits(String maskedSql, String... keywords) {
        List<KeywordHit> hits = new ArrayList<>();
        if (maskedSql == null || maskedSql.isEmpty()) {
            return hits;
        }
        int[] depth = depthArray(maskedSql);
        String upper = maskedSql.toUpperCase(Locale.ROOT);
        for (String keyword : keywords) {
            Pattern pattern = Pattern.compile("\\b" + Pattern.quote(keyword.toUpperCase(Locale.ROOT)) + "\\b");
            Matcher matcher = pattern.matcher(upper);
            while (matcher.find()) {
                if (depth[matcher.start()] == 0) {
                    hits.add(new KeywordHit(keyword, matcher.start(), matcher.end()));
                }
            }
        }
        hits.sort((a, b) -> Integer.compare(a.start(), b.start()));
        return hits;
    }

    /** 判断骨架文本中是否存在任意层级的某个关键字（词边界匹配）。 */
    public static boolean containsWord(String maskedSql, String keyword) {
        if (maskedSql == null || maskedSql.isEmpty()) {
            return false;
        }
        Pattern pattern = Pattern.compile("\\b" + Pattern.quote(keyword.toUpperCase(Locale.ROOT)) + "\\b");
        return pattern.matcher(maskedSql.toUpperCase(Locale.ROOT)).find();
    }

    /** 统计某关键字出现的次数（词边界、任意层级）。 */
    public static int countWord(String maskedSql, String keyword) {
        if (maskedSql == null || maskedSql.isEmpty()) {
            return 0;
        }
        Pattern pattern = Pattern.compile("\\b" + Pattern.quote(keyword.toUpperCase(Locale.ROOT)) + "\\b");
        Matcher matcher = pattern.matcher(maskedSql.toUpperCase(Locale.ROOT));
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    /**
     * 判断骨架文本中是否存在"以关键字为子串"的标识符。
     *
     * <p>与 {@link #containsWord} 的整词匹配不同，本方法允许关键字作为标识符的一部分：
     * {@code keyword=password} 可命中 {@code password_hash}、{@code user_password}。
     * 敏感列检查必须用这个语义 —— 整词匹配会漏掉 {@code password_hash}，
     * 而结果集侧的列过滤是按子串判断的，两侧语义必须一致。</p>
     */
    public static boolean containsWordLike(String maskedSql, String keyword) {
        if (maskedSql == null || maskedSql.isEmpty() || keyword == null || keyword.isBlank()) {
            return false;
        }
        Pattern pattern = Pattern.compile(
                "[A-Za-z0-9_$]*" + Pattern.quote(keyword.toUpperCase(Locale.ROOT)) + "[A-Za-z0-9_$]*");
        return pattern.matcher(maskedSql.toUpperCase(Locale.ROOT)).find();
    }

    /** 关键字命中位置。 */
    public record KeywordHit(String keyword, int start, int end) {
    }
}
