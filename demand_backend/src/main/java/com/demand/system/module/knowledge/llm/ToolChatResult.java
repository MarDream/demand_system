package com.demand.system.module.knowledge.llm;

import java.util.List;

/**
 * chatWithTools 的一轮结果：要么携带工具调用（需执行后回传继续循环），
 * 要么为纯文本（模型选择直接作答，循环结束）。
 */
public class ToolChatResult {

    private String content;
    private String reasoningContent;
    private List<ToolCallPayload> toolCalls;
    private LlmGateway.ChatUsage usage;
    /** stop / tool_calls（OpenAI）或 end_turn / tool_use（Anthropic） */
    private String finishReason;
    private long durationMs;

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getReasoningContent() { return reasoningContent; }
    public void setReasoningContent(String reasoningContent) { this.reasoningContent = reasoningContent; }

    public List<ToolCallPayload> getToolCalls() { return toolCalls; }
    public void setToolCalls(List<ToolCallPayload> toolCalls) { this.toolCalls = toolCalls; }

    public LlmGateway.ChatUsage getUsage() { return usage; }
    public void setUsage(LlmGateway.ChatUsage usage) { this.usage = usage; }

    public String getFinishReason() { return finishReason; }
    public void setFinishReason(String finishReason) { this.finishReason = finishReason; }

    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
}
