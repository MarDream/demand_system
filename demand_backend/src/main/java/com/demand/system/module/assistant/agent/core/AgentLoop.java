package com.demand.system.module.assistant.agent.core;

import com.demand.system.module.assistant.agent.dto.ToolCallRecord;
import com.demand.system.module.assistant.dto.AssistantSource;
import com.demand.system.module.knowledge.dto.KnowledgeSearchResponse;
import com.demand.system.module.knowledge.llm.AgentChatMessage;
import com.demand.system.module.knowledge.llm.LlmGateway;
import com.demand.system.module.knowledge.llm.LlmGatewayConfig;
import com.demand.system.module.knowledge.llm.ToolChatResult;
import com.demand.system.module.knowledge.llm.ToolDefinition;
import com.demand.system.module.nl2sql.dto.DataQueryResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 单 Agent 工具循环（ReAct）：模型自主决定调用工具 → 执行 → 结果回传 → 继续推理，
 * 直到模型给出直答或达到轮数/调用次数上限。
 * <p>写操作工具经 {@link AgentConfirmationRegistry} 挂起等待用户在前端确认。
 * 全程非流式；最终答案由编排层流式综合（或直答分段推送）。</p>
 */
@Component
public class AgentLoop {

    private static final Logger log = LoggerFactory.getLogger(AgentLoop.class);

    /** 最大推理轮数（每轮可含多个工具调用） */
    public static final int MAX_ROUNDS = 6;
    /** 单次消息的工具调用总数上限 */
    public static final int MAX_TOOL_CALLS = 12;
    /** 单个工具执行超时（秒）；确认等待另计 */
    private static final long TOOL_TIMEOUT_SECONDS = 60;
    /** 回传模型的单条工具结果长度上限，避免上下文膨胀 */
    private static final int TOOL_RESULT_CAP = 4000;

    private final LlmGateway llmGateway;
    private final AgentConfirmationRegistry confirmationRegistry;
    private final ObjectMapper objectMapper;
    private final Executor toolExecutor;

    public AgentLoop(LlmGateway llmGateway,
                     AgentConfirmationRegistry confirmationRegistry,
                     ObjectMapper objectMapper,
                     @Qualifier("agentTaskExecutor") Executor toolExecutor) {
        this.llmGateway = llmGateway;
        this.confirmationRegistry = confirmationRegistry;
        this.objectMapper = objectMapper;
        this.toolExecutor = toolExecutor;
    }

    /** 循环入参 */
    public record LoopRequest(
            LlmGatewayConfig.Provider provider,
            BigDecimal temperature,
            Integer maxTokens,
            String systemPrompt,
            String instruction,
            List<AgentTool> tools,
            AgentToolContext context,
            /** Agent 标识：主循环 "main"，子 Agent 为子任务 ID */
            String agentId,
            /** 确认挂起归属的会话（SSE 断开时按会话拒绝） */
            Long sessionId,
            LoopListener listener
    ) {}

    /** SSE 回调（实现方自行决定发送失败时的处理策略） */
    public interface LoopListener {
        default void onToolCall(ToolCallRecord record) {}
        default void onPermissionRequest(String requestId, ToolCallRecord record) {}
    }

    /** 循环产出：直答（可空）+ 全部工具证据 */
    public static class LoopResult {
        private String directAnswer;
        private String reasoning;
        private final List<ToolCallRecord> toolCalls = new ArrayList<>();
        private final List<AssistantSource> sources = new ArrayList<>();
        private final List<KnowledgeSearchResponse.CitationReference> citations = new ArrayList<>();
        private DataQueryResult dataResult;
        private final List<String> warnings = new ArrayList<>();
        private int rounds;
        private int promptTokens;
        private int completionTokens;

        public String getDirectAnswer() { return directAnswer; }
        public String getReasoning() { return reasoning; }
        public List<ToolCallRecord> getToolCalls() { return toolCalls; }
        public List<AssistantSource> getSources() { return sources; }
        public List<KnowledgeSearchResponse.CitationReference> getCitations() { return citations; }
        public DataQueryResult getDataResult() { return dataResult; }
        public List<String> getWarnings() { return warnings; }
        public int getRounds() { return rounds; }
        public int getPromptTokens() { return promptTokens; }
        public int getCompletionTokens() { return completionTokens; }
    }

    public LoopResult run(LoopRequest request) {
        LoopResult result = new LoopResult();
        Map<String, AgentTool> toolMap = new HashMap<>();
        List<ToolDefinition> definitions = new ArrayList<>();
        for (AgentTool tool : request.tools()) {
            toolMap.put(tool.name(), tool);
            definitions.add(new ToolDefinition(tool.name(), tool.description(), tool.schema()));
        }

        List<AgentChatMessage> messages = new ArrayList<>();
        messages.add(AgentChatMessage.user(request.instruction()));

        request.context().setChatProvider(request.provider());

        int totalToolCalls = 0;
        for (int round = 1; round <= MAX_ROUNDS; round++) {
            result.rounds = round;
            ToolChatResult response = llmGateway.chatWithTools(
                    request.provider(),
                    request.systemPrompt(),
                    messages,
                    definitions,
                    request.temperature(),
                    request.maxTokens());
            accumulateUsage(result, response.getUsage());

            if (!response.hasToolCalls()) {
                // 模型选择直接作答：循环结束，答案交编排层推送
                result.directAnswer = response.getContent();
                result.reasoning = response.getReasoningContent();
                return result;
            }
            messages.add(AgentChatMessage.assistantWithToolCalls(response.getToolCalls(),
                    response.getContent() == null ? "" : response.getContent()));

            for (var call : response.getToolCalls()) {
                totalToolCalls++;
                if (totalToolCalls > MAX_TOOL_CALLS) {
                    messages.add(AgentChatMessage.toolResult(call.getId(), call.getName(),
                            "已达到工具调用次数上限，请基于已有信息作答。"));
                    continue;
                }
                // 澄清提问：把问题作为本轮回答直接推给用户并终止循环，
                // 用户在下一条消息中答复后由新一轮请求继续任务
                if (com.demand.system.module.assistant.agent.tools.AskUserTool.TOOL_NAME.equals(call.getName())) {
                    JsonNode askArgs = parseArguments(call.getArgumentsJson());
                    String question = askArgs.path("question").asText("");
                    ToolCallRecord askRecord = new ToolCallRecord(call.getId(), request.agentId(),
                            com.demand.system.module.assistant.agent.tools.AskUserTool.TOOL_NAME,
                            "向用户提问", truncate(question, 120));
                    askRecord.setStatus("completed");
                    askRecord.setResultPreview("已在回答中向用户提问");
                    askRecord.markStarted();
                    askRecord.markFinished();
                    result.toolCalls.add(askRecord);
                    request.listener().onToolCall(askRecord);
                    result.directAnswer = question.isBlank()
                            ? "为了准确完成你的需求，请补充一些关键信息。"
                            : question;
                    log.info("agent loop paused for user clarification, agentId={}", request.agentId());
                    return result;
                }
                executeToolCall(request, toolMap, call, messages, result);
            }
        }
        // 轮数用尽仍无直答：去掉工具做一次收尾作答，保证循环总有产出
        log.info("agent loop reached max rounds, finalizing without tools, agentId={}", request.agentId());
        ToolChatResult finalResponse = llmGateway.chatWithTools(
                request.provider(), request.systemPrompt(), messages, List.of(),
                request.temperature(), request.maxTokens());
        accumulateUsage(result, finalResponse.getUsage());
        result.directAnswer = finalResponse.getContent();
        result.reasoning = finalResponse.getReasoningContent();
        return result;
    }

    private void executeToolCall(LoopRequest request,
                                 Map<String, AgentTool> toolMap,
                                 com.demand.system.module.knowledge.llm.ToolCallPayload call,
                                 List<AgentChatMessage> messages,
                                 LoopResult result) {
        AgentTool tool = toolMap.get(call.getName());
        String callId = call.getId() == null || call.getId().isBlank()
                ? "tc-" + UUID.randomUUID().toString().substring(0, 8) : call.getId();
        JsonNode args = parseArguments(call.getArgumentsJson());

        if (tool == null) {
            ToolCallRecord record = new ToolCallRecord(callId, request.agentId(), call.getName(),
                    call.getName(), previewOf(null, args));
            record.setStatus("failed");
            record.setResultPreview("未知工具");
            record.markStarted();
            record.markFinished();
            result.toolCalls.add(record);
            request.listener().onToolCall(record);
            messages.add(AgentChatMessage.toolResult(callId, call.getName(),
                    "未知工具：" + call.getName()));
            return;
        }

        ToolCallRecord record = new ToolCallRecord(callId, request.agentId(), tool.name(),
                tool.title(), previewOf(tool, args));
        result.toolCalls.add(record);
        request.listener().onToolCall(record);

        // --- 写操作：先经用户确认 ---
        if (tool.needsConfirmation()) {
            record.setStatus("awaiting_confirm");
            request.listener().onToolCall(record);
            String requestId = UUID.randomUUID().toString();
            confirmationRegistry.register(requestId, request.context().getUserId(), request.sessionId());
            request.listener().onPermissionRequest(requestId, record);
            boolean approved = confirmationRegistry.await(requestId, AgentConfirmationRegistry.CONFIRM_TIMEOUT_MS);
            if (!approved) {
                record.setStatus("denied");
                record.setResultPreview("用户未确认，操作未执行");
                record.markStarted();
                record.markFinished();
                request.listener().onToolCall(record);
                messages.add(AgentChatMessage.toolResult(callId, tool.name(),
                        "用户未确认该操作，未执行。请勿再次尝试，继续回答或说明即可。"));
                return;
            }
            record.setStatus("confirmed");
            request.listener().onToolCall(record);
        }

        record.setStatus("running");
        record.markStarted();
        request.listener().onToolCall(record);

        try {
            AgentToolResult toolResult = CompletableFuture
                    .supplyAsync(() -> {
                        try {
                            return tool.execute(request.context(), args);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }, toolExecutor)
                    .get(TOOL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            record.setStatus("completed");
            String summary = toolResult.getUiSummary();
            record.setResultPreview(summary != null && !summary.isBlank()
                    ? summary : truncate(toolResult.getResultForLlm(), 160));
            record.markFinished();
            request.listener().onToolCall(record);

            result.sources.addAll(toolResult.getSources());
            result.citations.addAll(toolResult.getCitations());
            if (toolResult.getDataResult() != null) {
                result.dataResult = toolResult.getDataResult();
            }
            result.warnings.addAll(toolResult.getWarnings());
            messages.add(AgentChatMessage.toolResult(callId, tool.name(),
                    truncate(toolResult.getResultForLlm(), TOOL_RESULT_CAP)));
        } catch (TimeoutException e) {
            record.setStatus("failed");
            record.setResultPreview("工具执行超时（" + TOOL_TIMEOUT_SECONDS + "s）");
            record.markFinished();
            request.listener().onToolCall(record);
            messages.add(AgentChatMessage.toolResult(callId, tool.name(), "工具执行超时。"));
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            log.warn("agent tool execution failed, tool={}, agentId={}", tool.name(), request.agentId(), cause);
            record.setStatus("failed");
            record.setResultPreview(truncate("执行失败：" + cause.getMessage(), 160));
            record.markFinished();
            request.listener().onToolCall(record);
            messages.add(AgentChatMessage.toolResult(callId, tool.name(),
                    "工具执行失败：" + cause.getMessage()));
        }
    }

    private String previewOf(AgentTool tool, JsonNode args) {
        try {
            return tool != null ? tool.previewArgs(args) : (args == null ? "" : args.toString());
        } catch (Exception e) {
            return args == null ? "" : truncate(args.toString(), 160);
        }
    }

    private JsonNode parseArguments(String argumentsJson) {
        if (argumentsJson == null || argumentsJson.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(argumentsJson);
        } catch (Exception e) {
            return objectMapper.createObjectNode();
        }
    }

    private void accumulateUsage(LoopResult result, LlmGateway.ChatUsage usage) {
        if (usage == null) {
            return;
        }
        if (usage.getPromptTokens() != null) {
            result.promptTokens += usage.getPromptTokens();
        }
        if (usage.getCompletionTokens() != null) {
            result.completionTokens += usage.getCompletionTokens();
        }
    }

    private String truncate(String text, int cap) {
        if (text == null) {
            return "";
        }
        return text.length() <= cap ? text : text.substring(0, cap) + "…";
    }
}
