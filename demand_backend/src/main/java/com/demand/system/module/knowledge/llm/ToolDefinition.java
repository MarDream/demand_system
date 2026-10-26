package com.demand.system.module.knowledge.llm;

import java.util.Map;

/**
 * 提供给模型的工具定义（JSON Schema 描述），随 chatWithTools 请求下发。
 */
public class ToolDefinition {

    private String name;
    private String description;
    /** JSON Schema 的 parameters / input_schema 部分 */
    private Map<String, Object> parametersSchema;

    public ToolDefinition() {}

    public ToolDefinition(String name, String description, Map<String, Object> parametersSchema) {
        this.name = name;
        this.description = description;
        this.parametersSchema = parametersSchema;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Map<String, Object> getParametersSchema() { return parametersSchema; }
    public void setParametersSchema(Map<String, Object> parametersSchema) { this.parametersSchema = parametersSchema; }
}
