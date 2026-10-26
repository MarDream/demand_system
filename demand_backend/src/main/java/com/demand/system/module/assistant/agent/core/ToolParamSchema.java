package com.demand.system.module.assistant.agent.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 工具入参 JSON Schema 的链式构建器（生成 {"type":"object",...} 根结构） */
public final class ToolParamSchema {

    private final Map<String, Object> properties = new LinkedHashMap<>();
    private final List<String> required = new ArrayList<>();

    public static ToolParamSchema object() {
        return new ToolParamSchema();
    }

    /** 追加一个参数；description 为模型可见的参数说明 */
    public ToolParamSchema property(String name, String type, String description) {
        Map<String, Object> prop = new LinkedHashMap<>();
        prop.put("type", type);
        prop.put("description", description == null ? "" : description);
        properties.put(name, prop);
        return this;
    }

    /** 声明必填参数（须先 property） */
    public ToolParamSchema required(String... names) {
        for (String name : names) {
            if (properties.containsKey(name)) {
                required.add(name);
            }
        }
        return this;
    }

    public Map<String, Object> build() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        if (!required.isEmpty()) {
            schema.put("required", required);
        }
        return schema;
    }
}
