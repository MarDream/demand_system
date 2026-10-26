package com.demand.system.module.assistant.agent.core;

import com.demand.system.common.result.ErrorCode;
import com.demand.system.common.exception.BusinessException;
import com.demand.system.module.assistant.agent.dto.AgentPlan;
import com.demand.system.module.assistant.agent.dto.AgentSubTask;
import com.demand.system.module.assistant.agent.dto.AgentTrace;
import com.demand.system.module.assistant.agent.dto.SubAgentRun;
import com.demand.system.module.assistant.agent.dto.ToolCallRecord;
import com.demand.system.module.assistant.dto.AssistantAction;
import com.demand.system.module.assistant.dto.AssistantChatRequest;
import com.demand.system.module.assistant.dto.AssistantMessageVO;
import com.demand.system.module.assistant.dto.AssistantOperationAdvice;
import com.demand.system.module.assistant.dto.AssistantPageContext;
import com.demand.system.module.assistant.dto.AssistantSource;
import com.demand.system.module.assistant.dto.AssistantTask;
import com.demand.system.module.assistant.entity.AssistantMessage;
import com.demand.system.module.assistant.entity.AssistantSession;
import com.demand.system.module.assistant.entity.QuestionLog;
import com.demand.system.module.assistant.mapper.AssistantMessageMapper;
import com.demand.system.module.assistant.mapper.AssistantSessionMapper;
import com.demand.system.module.assistant.mapper.QuestionLogMapper;
import com.demand.system.module.knowledge.dto.KnowledgeSearchResponse;
import com.demand.system.module.knowledge.llm.LlmGateway;
import com.demand.system.module.knowledge.llm.LlmGatewayConfig;
import com.demand.system.module.llm.constant.LlmApplicationCode;
import com.demand.system.module.llm.service.LlmModelResolver;
import com.demand.system.module.nl2sql.dto.DataQueryResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * 智能体总编排：路由结论确认 → 任务规划 → 子 Agent 并行执行（多模型分派）→ 流式综合成稿。
 * <p>由通用助手分支在智能路由命中后调用；全程异常兜底降级回原通用链路（legacyFallback）。
 * 智能体分支不做正文 [N] 角标（跨工具重编号脆弱），引用口径与跨源摘要一致：注明来源文档名/工单编号。</p>
 */
@Component
public class AgentOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AgentOrchestrator.class);
    private static final String DEFAULT_SESSION_TITLE = "新会话";

    private final AgentModeRouter modeRouter;
    private final AgentPlanner planner;
    private final SubAgentRunner subAgentRunner;
    private final AgentConfirmationRegistry confirmationRegistry;
    private final LlmModelResolver llmModelResolver;
    private final LlmGateway llmGateway;
    private final AssistantMessageMapper messageMapper;
    private final AssistantSessionMapper sessionMapper;
    private final QuestionLogMapper questionLogMapper;
    private final Executor agentExecutor;

    public AgentOrchestrator(AgentModeRouter modeRouter,
                             AgentPlanner planner,
                             SubAgentRunner subAgentRunner,
                             AgentConfirmationRegistry confirmationRegistry,
                             LlmModelResolver llmModelResolver,
                             LlmGateway llmGateway,
                             AssistantMessageMapper messageMapper,
                             AssistantSessionMapper sessionMapper,
                             QuestionLogMapper questionLogMapper,
                             @org.springframework.beans.factory.annotation.Qualifier(
                                     "agentTaskExecutor") Executor agentExecutor) {
        this.modeRouter = modeRouter;
        this.planner = planner;
        this.subAgentRunner = subAgentRunner;
        this.confirmationRegistry = confirmationRegistry;
        this.llmModelResolver = llmModelResolver;
        this.llmGateway = llmGateway;
        this.messageMapper = messageMapper;
        this.sessionMapper = sessionMapper;
        this.questionLogMapper = questionLogMapper;
        this.agentExecutor = agentExecutor;
    }

    /** SSE 断开/超时时拒绝该会话全部挂起的写操作确认 */
    public void registerDisconnectGuard(SseEmitter emitter, Long sessionId) {
        Runnable guard = () -> confirmationRegistry.denyAllForSession(sessionId);
        emitter.onCompletion(guard);
        emitter.onTimeout(guard);
        emitter.onError(error -> guard.run());
    }

    /**
     * 智能体编排主入口（调用方已在 SSE 线程池上）。
     *
     * @param legacyFallback 编排失败且无任何产出时，降级执行的原始通用链路
     */
    public void orchestrate(SseEmitter emitter,
                            AssistantChatRequest request,
                            String userContent,
                            Long userMessageId,
                            AssistantMessage assistantMessage,
                            AssistantSession session,
                            Long questionLogId,
                            boolean superAdmin,
                            List<String> permissions,
                            AssistantOperationAdvice advice,
                            Runnable legacyFallback) {
        registerDisconnectGuard(emitter, session.getId());

        StringBuilder answer = new StringBuilder();
        StringBuilder reasoning = new StringBuilder();
        List<AssistantTask> tasks = new ArrayList<>();
        AgentTrace trace = new AgentTrace();
        boolean[] anyOutput = {false};
        int[] promptTokens = {0};
        int[] completionTokens = {0};

        try {
            emitter.send(SseEmitter.event().name("meta").data(Map.of(
                    "sessionId", assistantMessage.getSessionId(),
                    "userMessageId", userMessageId,
                    "assistantMessageId", assistantMessage.getId()
            )));

            Long userId = assistantMessage.getUserId();
            Long userSelectedModelId = request.getLlmModelId();

            // --- Task 1: 智能路由结论 ---
            AssistantTask routeTask = new AssistantTask("agent_route", "智能路由");
            tasks.add(routeTask);
            String routeReason = modeRouter.describe(AgentModeRouter.Decision.AGENT, userContent);
            trace.setRouteReason(routeReason);
            routeTask.start("评估问题特征");
            routeTask.complete(routeReason);
            pushTask(emitter, tasks, routeTask);

            // --- Task 2: 任务规划（多子任务才需要真正拆分） ---
            AssistantTask planTask = new AssistantTask("agent_plan", "任务规划");
            tasks.add(planTask);
            planTask.start("拆解用户问题");
            pushTask(emitter, tasks, planTask);
            SubAgentRunner.SubAgentModel plannerModel = resolveModel(
                    userSelectedModelId, LlmApplicationCode.ASSISTANT_AGENT_PLANNER);
            AgentPlan plan = planner.plan(userContent, request.getPageContext(),
                    plannerModel == null ? null : plannerModel.provider(),
                    plannerModel == null ? null : plannerModel.temperature(),
                    plannerModel == null ? null : plannerModel.maxTokens());
            trace.setPlan(plan);
            emitter.send(SseEmitter.event().name("plan").data(Map.of(
                    "plan", plan,
                    "routeReason", routeReason,
                    "plannerModel", plannerModel == null ? "" : plannerModel.label()
            )));
            if (plan.isMultiAgent()) {
                planTask.complete("拆分为 " + plan.getSubtasks().size() + " 个子任务");
            } else {
                planTask.complete("单任务执行（无需拆分）");
            }
            pushTask(emitter, tasks, planTask);

            // --- Task 3: 执行（单 Agent 直跑 / 多子 Agent 并行） ---
            AgentEventSink sink = buildSink(emitter, tasks);

            List<AgentLoop.LoopResult> loopResults = new ArrayList<>();
            List<SubAgentRun> runs = new ArrayList<>();

            if (!plan.isMultiAgent()) {
                // 单 Agent：主智能体带全部工具直接执行
                AgentSubTask mainTask = new AgentSubTask("general", "执行智能体", userContent);
                SubAgentRunner.SubAgentModel executorModel = requireModel(
                        userSelectedModelId, LlmApplicationCode.ASSISTANT_AGENT_EXECUTOR);
                AgentToolContext context = buildContext(userId, superAdmin, permissions, request, "main", sink, tasks);
                SubAgentRunner.SubAgentOutcome outcome =
                        subAgentRunner.run(mainTask, "main", executorModel, context, session.getId(), sink);
                runs.add(outcome.run());
                if (outcome.loopResult() != null) {
                    loopResults.add(outcome.loopResult());
                }
            } else {
                // 多子 Agent：按子任务类型分派不同功能点的模型并行执行
                List<CompletableFuture<SubAgentRunner.SubAgentOutcome>> futures = new ArrayList<>();
                List<AgentSubTask> subtasks = plan.getSubtasks();
                for (int i = 0; i < subtasks.size(); i++) {
                    AgentSubTask subTask = subtasks.get(i);
                    String agentId = "sub-" + (i + 1);
                    SubAgentRunner.SubAgentModel model = requireModel(userSelectedModelId, applicationCodeFor(subTask.getType()));
                    AgentToolContext context = buildContext(userId, superAdmin, permissions, request, agentId, sink, tasks);
                    futures.add(CompletableFuture.supplyAsync(
                            () -> subAgentRunner.run(subTask, agentId, model, context, session.getId(), sink),
                            agentExecutor));
                }
                for (CompletableFuture<SubAgentRunner.SubAgentOutcome> future : futures) {
                    SubAgentRunner.SubAgentOutcome outcome = future.join();
                    runs.add(outcome.run());
                    if (outcome.loopResult() != null) {
                        loopResults.add(outcome.loopResult());
                    }
                }
            }
            trace.setSubAgents(runs);
            if (!plan.isMultiAgent() && !runs.isEmpty()) {
                // 单 Agent 路径把工具调用平铺到顶层，前端面板直接可读
                trace.setToolCalls(runs.get(0).getToolCalls() == null ? List.of() : runs.get(0).getToolCalls());
            }
            int totalRounds = loopResults.stream().mapToInt(AgentLoop.LoopResult::getRounds).sum();
            trace.setRounds(totalRounds);
            loopResults.forEach(lr -> {
                promptTokens[0] += lr.getPromptTokens();
                completionTokens[0] += lr.getCompletionTokens();
            });

            // --- 证据合并（依据面板） ---
            List<KnowledgeSearchResponse.CitationReference> citations = new ArrayList<>();
            List<String> warnings = new ArrayList<>();
            DataQueryResult dataResult = null;
            for (AgentLoop.LoopResult loopResult : loopResults) {
                citations.addAll(loopResult.getCitations());
                warnings.addAll(loopResult.getWarnings());
                if (dataResult == null) {
                    dataResult = loopResult.getDataResult();
                }
            }
            List<AssistantSource> sources = new ArrayList<>(CitationSourceMapper.map(citations, null));

            assistantMessage.setSources(sources);
            assistantMessage.setCitations(citations);
            assistantMessage.setWarnings(warnings.isEmpty() ? null : warnings);
            assistantMessage.setDataResult(dataResult);

            // --- 成稿：单 Agent 直答分段推送 / 多 Agent 综合流式生成 ---
            if (!plan.isMultiAgent()) {
                SubAgentRun mainRun = runs.get(0);
                String report = mainRun.getSummary() == null ? "" : mainRun.getSummary();
                if (report.isBlank()) {
                    throw new IllegalStateException("智能体未产出任何结果");
                }
                emitter.send(SseEmitter.event().name("actions").data(Map.of(
                        "intent", "agent",
                        "actions", advice.getActions() == null ? List.of() : advice.getActions(),
                        "sources", sources,
                        "citations", citations,
                        "tasks", tasks,
                        "warnings", warnings
                )));
                AgentLoop.LoopResult mainLoop = loopResults.isEmpty() ? null : loopResults.get(0);
                if (mainLoop != null && mainLoop.getReasoning() != null && !mainLoop.getReasoning().isBlank()) {
                    reasoning.append(mainLoop.getReasoning());
                    emitter.send(SseEmitter.event().name("reasoning").data(reasoning.toString()));
                }
                for (int index = 0; index < report.length(); index += 24) {
                    String chunk = report.substring(index, Math.min(index + 24, report.length()));
                    answer.append(chunk);
                    anyOutput[0] = true;
                    emitter.send(SseEmitter.event().name("delta").data(chunk));
                }
            } else {
                SubAgentRunner.SubAgentModel synthModel = requireModel(
                        userSelectedModelId, LlmApplicationCode.ASSISTANT_CHAT);
                emitter.send(SseEmitter.event().name("actions").data(Map.of(
                        "intent", "agent",
                        "actions", advice.getActions() == null ? List.of() : advice.getActions(),
                        "sources", sources,
                        "citations", citations,
                        "tasks", tasks,
                        "warnings", warnings
                )));
                String systemPrompt = "你是企业需求系统的智能体综合专家。多个子智能体已完成资料检索与数据查询，"
                        + "你要把它们的简报综合成对用户的最终回答。\n"
                        + "要求：\n"
                        + "1. 直接回答用户问题或直接产出用户要的内容（报告/总结/分析要分章节、成结构，结论先行），"
                        + "而不是给出操作指引。\n"
                        + "2. 数据库数字必须原样引用简报中的查询结果，不得编造或改算。\n"
                        + "3. 引用知识库或工单内容时注明来源文档名或工单编号。\n"
                        + "4. 与问题无关的材料忽略。\n"
                        + "5. 子简报之间冲突时，以数据查询结果为准并说明差异。\n"
                        + "6. 子简报指出信息不足时，在回答末尾列出需要用户补充的关键信息。\n"
                        + "7. 不要输出 JSON。";
                String userPrompt = buildSynthesisUserPrompt(userContent, request, runs);
                Map<String, Object> thinkingParams = llmGateway.buildThinkingParams(synthModel.provider(), synthModel.maxTokens());
                try {
                    llmGateway.streamChatWithProvider(
                            synthModel.provider(), systemPrompt, userPrompt,
                            synthModel.temperature(), synthModel.maxTokens(), thinkingParams,
                            token -> {
                                try {
                                    answer.append(token);
                                    anyOutput[0] = true;
                                    emitter.send(SseEmitter.event().name("delta").data(token));
                                } catch (Exception e) {
                                    throw new RuntimeException(e);
                                }
                            },
                            token -> {
                                try {
                                    reasoning.append(token);
                                    emitter.send(SseEmitter.event().name("reasoningDelta").data(token));
                                } catch (Exception e) {
                                    throw new RuntimeException(e);
                                }
                            },
                            usage -> {
                                if (usage != null) {
                                    if (usage.getPromptTokens() != null) {
                                        promptTokens[0] += usage.getPromptTokens();
                                    }
                                    if (usage.getCompletionTokens() != null) {
                                        completionTokens[0] += usage.getCompletionTokens();
                                    }
                                }
                            });
                } catch (Exception streamError) {
                    if (answer.length() == 0) {
                        throw streamError;
                    }
                    log.warn("agent synthesis stream failed mid-flight, keep partial output", streamError);
                }
            }

            if (answer.length() == 0) {
                throw new IllegalStateException("智能体综合生成未产出内容");
            }

            // --- 落库与收尾 ---
            assistantMessage.setContent(answer.toString());
            assistantMessage.setReasoning(reasoning.length() > 0 ? reasoning.toString() : null);
            assistantMessage.setStatus("completed");
            assistantMessage.setIntent("agent");
            assistantMessage.setTasks(tasks);
            assistantMessage.setAgentPlan(plan);
            assistantMessage.setAgentTrace(trace);
            assistantMessage.setInputTokens(promptTokens[0] > 0 ? promptTokens[0] : null);
            assistantMessage.setOutputTokens(completionTokens[0] > 0 ? completionTokens[0] : null);
            assistantMessage.setTotalTokens(promptTokens[0] + completionTokens[0] > 0
                    ? promptTokens[0] + completionTokens[0] : null);
            messageMapper.updateById(assistantMessage);
            markQuestionAnswered(questionLogId, true);
            updateSessionTitle(session, userContent);

            emitter.send(SseEmitter.event().name("done").data(toMessageVO(assistantMessage)));
            emitter.complete();
        } catch (Exception e) {
            log.warn("agent orchestration failed, sessionId={}", assistantMessage.getSessionId(), e);
            if (!anyOutput[0] && legacyFallback != null) {
                // 未产出任何内容：降级回原通用链路，用户仍能得到回答
                try {
                    legacyFallback.run();
                    return;
                } catch (Exception legacyError) {
                    log.warn("agent legacy fallback also failed", legacyError);
                }
            }
            String fallback = "智能体处理失败：" + (e.getMessage() != null ? e.getMessage() : "请稍后重试");
            if (answer.length() == 0) {
                answer.append(fallback);
            }
            assistantMessage.setContent(answer.toString());
            assistantMessage.setReasoning(reasoning.length() > 0 ? reasoning.toString() : null);
            assistantMessage.setStatus("failed");
            assistantMessage.setIntent("agent");
            assistantMessage.setTasks(tasks);
            assistantMessage.setAgentTrace(trace);
            messageMapper.updateById(assistantMessage);
            markQuestionAnswered(questionLogId, false);
            try {
                emitter.send(SseEmitter.event().name("error").data(Map.of("message", fallback)));
            } catch (Exception sendError) {
                log.debug("SSE 发送 error 事件失败(客户端可能已断开)", sendError);
            }
            emitter.completeWithError(e);
        }
    }

    private AgentEventSink buildSink(SseEmitter emitter, List<AssistantTask> tasks) {
        return new AgentEventSink() {
            @Override
            public void onSubAgent(SubAgentRun run) {
                try {
                    emitter.send(SseEmitter.event().name("subagent").data(run));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public void onToolCall(ToolCallRecord record) {
                try {
                    emitter.send(SseEmitter.event().name("toolCall").data(record));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public void onPermissionRequest(String requestId, ToolCallRecord record) {
                try {
                    emitter.send(SseEmitter.event().name("permissionRequest").data(Map.of(
                            "requestId", requestId,
                            "toolCallId", record.getId(),
                            "toolName", record.getToolName(),
                            "title", record.getTitle(),
                            "argsPreview", record.getArgsPreview() == null ? "" : record.getArgsPreview()
                    )));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public void onTask(AssistantTask task) {
                pushTask(emitter, tasks, task);
            }
        };
    }

    private AgentToolContext buildContext(Long userId,
                                          boolean superAdmin,
                                          List<String> permissions,
                                          AssistantChatRequest request,
                                          String agentId,
                                          AgentEventSink sink,
                                          List<AssistantTask> tasks) {
        // 工具过程日志写到当前 Agent 的任务卡片，经 sink 推送
        return new AgentToolContext(
                userId,
                superAdmin,
                permissions,
                request.getPageContext(),
                request.getHistory(),
                request.getLlmModelId(),
                (level, message) -> {
                    tasks.stream()
                            .filter(t -> agentId.equals(t.getId()))
                            .findFirst()
                            .ifPresent(target -> {
                                target.log(level, message);
                                sink.onTask(target);
                            });
                });
    }

    /** 任务状态推送并合并进任务列表（保持与消息体一致） */
    private void pushTask(SseEmitter emitter, List<AssistantTask> tasks, AssistantTask task) {
        int index = -1;
        for (int i = 0; i < tasks.size(); i++) {
            if (Objects.equals(tasks.get(i).getId(), task.getId())) {
                index = i;
                break;
            }
        }
        if (index >= 0) {
            tasks.set(index, task);
        } else {
            tasks.add(task);
        }
        try {
            emitter.send(SseEmitter.event().name("taskUpdate").data(task));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String buildSynthesisUserPrompt(String userContent,
                                            AssistantChatRequest request,
                                            List<SubAgentRun> runs) {
        StringBuilder sb = new StringBuilder();
        sb.append("【用户问题】").append(userContent).append('\n');
        if (request.getFiles() != null && !request.getFiles().isEmpty()) {
            sb.append("（用户附加了 ").append(request.getFiles().size()).append(" 个文件，本轮未解析，可提示用户用知识库模式提问）\n");
        }
        sb.append("\n【各子智能体简报】\n");
        int index = 0;
        for (SubAgentRun run : runs) {
            index++;
            sb.append("### 简报").append(index).append("：").append(run.getTitle())
                    .append("（类型 ").append(run.getType())
                    .append("，模型 ").append(run.getModelLabel() == null ? "未知" : run.getModelLabel())
                    .append("）\n");
            sb.append(run.getSummary() == null ? "（无产出）" : run.getSummary()).append('\n');
            if (run.getToolCalls() != null && !run.getToolCalls().isEmpty()) {
                sb.append("使用工具：");
                for (ToolCallRecord toolCall : run.getToolCalls()) {
                    sb.append(toolCall.getTitle()).append("（").append(toolCall.getStatus()).append("）");
                    if (toolCall.getResultPreview() != null && !toolCall.getResultPreview().isBlank()) {
                        sb.append(" → ").append(shorten(toolCall.getResultPreview(), 80));
                    }
                    sb.append("；");
                }
                sb.append('\n');
            }
        }
        sb.append("\n请综合以上简报，直接回答用户问题。\n");
        return sb.toString();
    }

    private String shorten(String text, int cap) {
        if (text == null) {
            return "";
        }
        return text.length() <= cap ? text : text.substring(0, cap) + "…";
    }

    private SubAgentRunner.SubAgentModel requireModel(Long userModelId, String applicationCode) {
        SubAgentRunner.SubAgentModel model = resolveModel(userModelId, applicationCode);
        if (model == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "未配置可用的对话模型，请到【系统设置 → 模型配置】检查 assistant.chat 相关配置");
        }
        return model;
    }

    /** 功能点模型解析：用户手选 → 功能点配置 → assistant.chat 兜底 */
    private SubAgentRunner.SubAgentModel resolveModel(Long userModelId, String applicationCode) {
        try {
            LlmModelResolver.ResolvedModel resolved = null;
            if (userModelId != null) {
                resolved = llmModelResolver.resolveModel(userModelId, applicationCode);
            }
            if (resolved == null) {
                resolved = llmModelResolver.resolveFirst(applicationCode);
            }
            if (resolved == null && !LlmApplicationCode.ASSISTANT_CHAT.equals(applicationCode)) {
                if (userModelId != null) {
                    resolved = llmModelResolver.resolveModel(userModelId, LlmApplicationCode.ASSISTANT_CHAT);
                }
                if (resolved == null) {
                    resolved = llmModelResolver.resolveFirst(LlmApplicationCode.ASSISTANT_CHAT);
                }
            }
            if (resolved == null) {
                return null;
            }
            String label = resolved.model().getName() != null && !resolved.model().getName().isBlank()
                    ? resolved.model().getName() : resolved.model().getModelId();
            return new SubAgentRunner.SubAgentModel(
                    llmModelResolver.toGatewayProvider(resolved),
                    resolved.model().getTemperature(),
                    resolved.model().getMaxTokens(),
                    label);
        } catch (Exception e) {
            log.warn("resolve agent model failed, application={}", applicationCode, e);
            return null;
        }
    }

    private String applicationCodeFor(String subTaskType) {
        return switch (subTaskType == null ? "general" : subTaskType) {
            case "research" -> LlmApplicationCode.ASSISTANT_AGENT_RESEARCHER;
            case "data_analysis" -> LlmApplicationCode.ASSISTANT_AGENT_ANALYST;
            default -> LlmApplicationCode.ASSISTANT_AGENT_EXECUTOR;
        };
    }

    private void markQuestionAnswered(Long questionLogId, boolean answered) {
        if (questionLogId == null) {
            return;
        }
        try {
            QuestionLog update = new QuestionLog();
            update.setId(questionLogId);
            update.setAnswered(answered ? 1 : 0);
            questionLogMapper.updateById(update);
        } catch (Exception e) {
            log.debug("回写提问回答状态失败: questionLogId={}", questionLogId, e);
        }
    }

    private void updateSessionTitle(AssistantSession session, String userContent) {
        String currentTitle = session.getTitle();
        if (currentTitle != null && !currentTitle.isBlank() && !DEFAULT_SESSION_TITLE.equals(currentTitle)) {
            return;
        }
        try {
            AssistantSession update = new AssistantSession();
            update.setId(session.getId());
            String preview = userContent.replaceAll("\\s+", " ").trim();
            update.setTitle(preview.length() > 120 ? preview.substring(0, 120) : preview);
            sessionMapper.updateById(update);
        } catch (Exception e) {
            log.debug("更新会话标题失败: sessionId={}", session.getId(), e);
        }
    }

    private AssistantMessageVO toMessageVO(AssistantMessage message) {
        AssistantMessageVO vo = new AssistantMessageVO();
        vo.setId(message.getId());
        vo.setSessionId(message.getSessionId());
        vo.setRole(message.getRole());
        vo.setContent(message.getContent());
        vo.setStatus(message.getStatus());
        vo.setIntent(message.getIntent());
        vo.setPageContext(message.getPageContext());
        vo.setActions(message.getActions() == null ? List.of() : message.getActions());
        vo.setSources(message.getSources() == null ? List.of() : message.getSources());
        vo.setThinkingSteps(message.getThinkingSteps());
        vo.setTasks(message.getTasks());
        vo.setProcessSummary(message.getProcessSummary());
        vo.setRetrievedCount(message.getRetrievedCount());
        vo.setCitations(message.getCitations());
        vo.setWarnings(message.getWarnings());
        vo.setSuggestedFollowUps(message.getSuggestedFollowUps());
        vo.setReasoning(message.getReasoning());
        vo.setDataResult(message.getDataResult());
        vo.setInputTokens(message.getInputTokens());
        vo.setOutputTokens(message.getOutputTokens());
        vo.setTotalTokens(message.getTotalTokens());
        vo.setCreatedAt(message.getCreatedAt());
        vo.setAgentPlan(message.getAgentPlan());
        vo.setAgentTrace(message.getAgentTrace());
        return vo;
    }
}
