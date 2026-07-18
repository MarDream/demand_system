package com.demand.system.module.bitable.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 多维表格 JSON 字段序列化工具。
 * 数据库使用 JSON/TEXT 字段保存，接口层使用 Object 以便前端直接传对象/数组。
 */
public final class BitableJsonUtils {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private BitableJsonUtils() {
    }

    public static String toJsonString(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String str) {
            String trimmed = str.trim();
            return trimmed.isEmpty() ? null : str;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("JSON序列化失败", e);
        }
    }

    public static Object parseJson(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (!(trimmed.startsWith("{") || trimmed.startsWith("["))) {
            return value;
        }
        try {
            return OBJECT_MAPPER.readValue(trimmed, Object.class);
        } catch (JsonProcessingException e) {
            return value;
        }
    }
}
