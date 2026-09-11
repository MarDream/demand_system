package com.demand.system.module.knowledge.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 从 LLM 返回文本中提取并解析 JSON 的工具。
 * <p>LLM 输出常被 markdown 代码块或说明文字包裹，此处做容错提取；
 * 解析失败返回 null，由调用方决定降级行为。</p>
 */
public final class LlmJsonExtractor {

    private static final Logger log = LoggerFactory.getLogger(LlmJsonExtractor.class);

    private LlmJsonExtractor() {
    }

    /**
     * 从 LLM 输出中提取 JSON 字符串。
     * 依次尝试：直接解析、markdown 代码块、首尾大括号截取。
     *
     * @return JSON 字符串；提取失败返回 null
     */
    public static String extract(String llmContent) {
        if (llmContent == null || llmContent.isBlank()) {
            return null;
        }
        String trimmed = llmContent.trim();
        if (trimmed.startsWith("{")) {
            int end = trimmed.lastIndexOf("}");
            if (end > 0) {
                return trimmed.substring(0, end + 1);
            }
        }
        int blockStart = trimmed.indexOf("```json");
        if (blockStart >= 0) {
            int contentStart = blockStart + "```json".length();
            int blockEnd = trimmed.indexOf("```", contentStart);
            if (blockEnd > contentStart) {
                return trimmed.substring(contentStart, blockEnd).trim();
            }
        }
        int braceStart = trimmed.indexOf('{');
        int braceEnd = trimmed.lastIndexOf('}');
        if (braceStart >= 0 && braceEnd > braceStart) {
            return trimmed.substring(braceStart, braceEnd + 1);
        }
        return null;
    }

    /**
     * 提取并反序列化为指定类型。
     *
     * @return 解析结果；提取或解析失败返回 null
     */
    public static <T> T parse(ObjectMapper objectMapper, String llmContent, Class<T> type) {
        String json = extract(llmContent);
        if (json == null) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            return objectMapper.treeToValue(root, type);
        } catch (Exception e) {
            log.warn("LLM JSON 解析失败: {}", json, e);
            return null;
        }
    }
}
