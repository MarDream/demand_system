package com.demand.system.module.knowledge.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 工具调用协议层的报文组装与响应解析（不发起真实 HTTP） */
class LlmGatewayToolCallTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private LlmGateway gateway;
    private LlmGatewayConfig.Provider provider;

    @BeforeEach
    void setUp() {
        gateway = new LlmGateway(new LlmGatewayConfig(), objectMapper);
        provider = new LlmGatewayConfig.Provider();
        provider.setBaseUrl("https://api.example.com/v1");
        provider.setApiKey("test-key");
        provider.setModel("test-model");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> bodyOf(Map<String, Object> body) {
        return body;
    }

    @Test
    void openAiBodyCarriesToolsAndToolRoundTrip() {
        List<AgentChatMessage> messages = List.of(
                AgentChatMessage.user("查一下本月工单"),
                AgentChatMessage.assistantWithToolCalls(
                        List.of(new ToolCallPayload("call-1", "data_query", "{\"question\":\"本月工单量\"}")), ""),
                AgentChatMessage.toolResult("call-1", "data_query", "共 12 条")
        );
        Map<String, Object> body = gateway.buildOpenAIToolBody(provider, "系统提示",
                messages, List.of(simpleTool()), null, null);

        assertEquals("auto", body.get("tool_choice"));
        List<Map<String, Object>> msgs = (List<Map<String, Object>>) body.get("messages");
        assertEquals(4, msgs.size()); // system + user + assistant + tool
        assertEquals("system", msgs.get(0).get("role"));
        assertEquals("assistant", msgs.get(2).get("role"));
        List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) msgs.get(2).get("tool_calls");
        assertEquals("call-1", toolCalls.get(0).get("id"));
        assertEquals("tool", msgs.get(3).get("role"));
        assertEquals("call-1", msgs.get(3).get("tool_call_id"));
    }

    @Test
    void anthropicBodyMergesConsecutiveToolResultsIntoOneUserMessage() throws Exception {
        List<AgentChatMessage> messages = List.of(
                AgentChatMessage.user("对比两个数据"),
                AgentChatMessage.assistantWithToolCalls(List.of(
                        new ToolCallPayload("t1", "data_query", "{\"q\":\"a\"}"),
                        new ToolCallPayload("t2", "knowledge_search", "{\"q\":\"b\"}")), "先取数"),
                AgentChatMessage.toolResult("t1", "data_query", "结果A"),
                AgentChatMessage.toolResult("t2", "knowledge_search", "结果B"),
                AgentChatMessage.user("继续")
        );
        Map<String, Object> body = gateway.buildAnthropicToolBody(provider, "sys", messages,
                List.of(simpleTool()), null, null);
        List<Map<String, Object>> msgs = (List<Map<String, Object>>) body.get("messages");
        // user / assistant / user(tool_result x2) / user
        assertEquals(4, msgs.size());
        List<Map<String, Object>> toolResultBlocks = (List<Map<String, Object>>) msgs.get(2).get("content");
        assertEquals(2, toolResultBlocks.size());
        assertEquals("tool_result", toolResultBlocks.get(0).get("type"));
        assertEquals("t2", toolResultBlocks.get(1).get("tool_use_id"));

        List<Map<String, Object>> assistantBlocks = (List<Map<String, Object>>) msgs.get(1).get("content");
        assertEquals(3, assistantBlocks.size()); // text + tool_use x2
        assertEquals("tool_use", assistantBlocks.get(1).get("type"));
        JsonNode input = objectMapper.valueToTree(assistantBlocks.get(1).get("input"));
        assertEquals("a", input.path("q").asText());
    }

    @Test
    void parseOpenAiToolCallsResponse() throws Exception {
        String json = """
                {
                  "choices": [{
                    "finish_reason": "tool_calls",
                    "message": {
                      "content": null,
                      "tool_calls": [{
                        "id": "call-9",
                        "type": "function",
                        "function": {"name": "data_query", "arguments": "{\\"q\\":\\"本月\\"}"}
                      }]
                    }
                  }],
                  "usage": {"prompt_tokens": 100, "completion_tokens": 20, "total_tokens": 120}
                }
                """;
        ToolChatResult result = gateway.parseToolChatResult(objectMapper.readTree(json),
                LlmGatewayConfig.Protocol.OPENAI, 5L);
        assertTrue(result.hasToolCalls());
        assertEquals("call-9", result.getToolCalls().get(0).getId());
        assertEquals("data_query", result.getToolCalls().get(0).getName());
        assertEquals("tool_calls", result.getFinishReason());
        assertEquals(120, result.getUsage().getTotalTokens());
    }

    @Test
    void parseOpenAiPlainAnswer() throws Exception {
        String json = """
                {"choices": [{"finish_reason": "stop", "message": {"content": "直接回答"}}]}
                """;
        ToolChatResult result = gateway.parseToolChatResult(objectMapper.readTree(json),
                LlmGatewayConfig.Protocol.OPENAI, 5L);
        assertFalse(result.hasToolCalls());
        assertEquals("直接回答", result.getContent());
    }

    @Test
    void parseAnthropicToolUseBlocks() throws Exception {
        String json = """
                {
                  "stop_reason": "tool_use",
                  "content": [
                    {"type": "thinking", "thinking": "需要检索"},
                    {"type": "text", "text": "先查一下"},
                    {"type": "tool_use", "id": "tu-1", "name": "knowledge_search", "input": {"query": "流程"}}
                  ],
                  "usage": {"input_tokens": 30, "output_tokens": 10}
                }
                """;
        ToolChatResult result = gateway.parseToolChatResult(objectMapper.readTree(json),
                LlmGatewayConfig.Protocol.ANTHROPIC, 5L);
        assertTrue(result.hasToolCalls());
        assertEquals("tu-1", result.getToolCalls().get(0).getId());
        assertEquals("{\"query\":\"流程\"}", result.getToolCalls().get(0).getArgumentsJson());
        assertEquals("需要检索", result.getReasoningContent());
        assertEquals("先查一下", result.getContent());
        assertEquals(30, result.getUsage().getPromptTokens());
        assertNull(result.getUsage().getTotalTokens());
    }

    private ToolDefinition simpleTool() {
        return new ToolDefinition("data_query", "数据查询",
                Map.of("type", "object", "properties", Map.of("q", Map.of("type", "string"))));
    }
}
