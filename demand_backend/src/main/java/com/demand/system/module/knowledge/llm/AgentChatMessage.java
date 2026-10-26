package com.demand.system.module.knowledge.llm;

import java.util.List;

/**
 * 智能体多轮消息，支持 tool call 往返，供 {@link LlmGateway#chatWithTools} 使用。
 * <p>role 取值：user / assistant / tool。system 提示词独立传入，不混入消息列表。</p>
 */
public class AgentChatMessage {

    private String role;
    private String content;
    /** tool 消息对应的调用 ID */
    private String toolCallId;
    /** tool 消息的工具名 */
    private String name;
    /** assistant 消息携带的工具调用列表 */
    private List<ToolCallPayload> toolCalls;

    public AgentChatMessage() {}

    private AgentChatMessage(String role, String content, String toolCallId, String name,
                             List<ToolCallPayload> toolCalls) {
        this.role = role;
        this.content = content;
        this.toolCallId = toolCallId;
        this.name = name;
        this.toolCalls = toolCalls;
    }

    public static AgentChatMessage user(String content) {
        return new AgentChatMessage("user", content, null, null, null);
    }

    public static AgentChatMessage assistant(String content) {
        return new AgentChatMessage("assistant", content, null, null, null);
    }

    public static AgentChatMessage assistantWithToolCalls(List<ToolCallPayload> toolCalls, String content) {
        return new AgentChatMessage("assistant", content, null, null, toolCalls);
    }

    public static AgentChatMessage toolResult(String toolCallId, String toolName, String content) {
        return new AgentChatMessage("tool", content, toolCallId, toolName, null);
    }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getToolCallId() { return toolCallId; }
    public void setToolCallId(String toolCallId) { this.toolCallId = toolCallId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public List<ToolCallPayload> getToolCalls() { return toolCalls; }
    public void setToolCalls(List<ToolCallPayload> toolCalls) { this.toolCalls = toolCalls; }
}
