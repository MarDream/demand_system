package com.demand.system.module.knowledge.llm;

/**
 * 模型发起的一次工具调用（OpenAI tool_calls 项 / Anthropic tool_use block 的统一表示）。
 * <p>argumentsJson 保留模型返回的原始 JSON 字符串，由工具层自行解析校验。</p>
 */
public class ToolCallPayload {

    private String id;
    private String name;
    /** 工具入参（JSON 字符串，可能为空串） */
    private String argumentsJson;

    public ToolCallPayload() {}

    public ToolCallPayload(String id, String name, String argumentsJson) {
        this.id = id;
        this.name = name;
        this.argumentsJson = argumentsJson;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getArgumentsJson() { return argumentsJson; }
    public void setArgumentsJson(String argumentsJson) { this.argumentsJson = argumentsJson; }
}
