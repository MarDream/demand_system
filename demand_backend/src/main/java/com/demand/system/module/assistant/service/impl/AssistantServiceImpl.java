package com.demand.system.module.assistant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.demand.system.common.config.SseExecutorConfig;
import com.demand.system.common.exception.BusinessException;
import com.demand.system.common.result.ErrorCode;
import com.demand.system.module.assistant.dto.AssistantAction;
import com.demand.system.module.assistant.dto.AssistantSource;
import com.demand.system.module.assistant.dto.AssistantChatRequest;
import com.demand.system.module.assistant.dto.AssistantFileAttachment;
import com.demand.system.module.assistant.dto.AssistantMessageVO;
import com.demand.system.module.assistant.dto.AssistantOperationAdvice;
import com.demand.system.module.assistant.dto.AssistantRegenerateRequest;
import com.demand.system.module.assistant.dto.AssistantSessionCreateDTO;
import com.demand.system.module.assistant.dto.AssistantSessionVO;
import com.demand.system.module.assistant.dto.AssistantTask;
import com.demand.system.module.assistant.entity.AssistantMessage;
import com.demand.system.module.assistant.entity.AssistantSession;
import com.demand.system.module.assistant.entity.QuestionLog;
import com.demand.system.module.assistant.mapper.AssistantMessageMapper;
import com.demand.system.module.assistant.mapper.AssistantSessionMapper;
import com.demand.system.module.assistant.mapper.QuestionLogMapper;
import com.demand.system.module.assistant.service.AssistantOperationCatalogService;
import com.demand.system.module.assistant.service.AssistantService;
import com.demand.system.module.assistant.validator.AssistantActionValidator;
import com.demand.system.module.auth.security.SecurityUtils;
import com.demand.system.module.knowledge.constant.KnowledgeSearchScope;
import com.demand.system.module.knowledge.dto.KnowledgeSearchRequest;
import com.demand.system.module.knowledge.dto.KnowledgeSearchResponse;
import com.demand.system.module.knowledge.dto.QueryRewriteResult;
import com.demand.system.module.knowledge.service.KnowledgeSearchService;
import com.demand.system.module.knowledge.service.QueryRewriteService;
import com.demand.system.module.knowledge.service.RagAnswerService;
import com.demand.system.module.knowledge.llm.LlmGateway;
import com.demand.system.module.knowledge.llm.LlmGatewayConfig;
import com.demand.system.module.llm.constant.LlmApplicationCode;
import com.demand.system.module.llm.service.LlmModelResolver;
import com.demand.system.module.nl2sql.dto.DataQueryResult;
import com.demand.system.module.nl2sql.dto.Nl2SqlContext;
import com.demand.system.module.nl2sql.dto.Nl2SqlOutcome;
import com.demand.system.module.nl2sql.service.Nl2SqlAnswerStream;
import com.demand.system.module.nl2sql.service.Nl2SqlService;
import com.demand.system.module.requirement.service.impl.RequirementServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Service
public class AssistantServiceImpl implements AssistantService {
    private static final Logger log = LoggerFactory.getLogger(AssistantServiceImpl.class);
    private static final long STREAM_TIMEOUT_MS = 120_000L;
    private static final String DEFAULT_SESSION_TITLE = "新会话";
    private static final String NO_RESULT_HINT = "未在所选知识库中找到相关内容，建议换个问法，或切换到其它知识库范围后再试。";

    private final AssistantSessionMapper sessionMapper;
    private final AssistantMessageMapper messageMapper;
    private final QuestionLogMapper questionLogMapper;
    private final AssistantOperationCatalogService catalogService;
    private final AssistantActionValidator actionValidator;
    private final LlmGateway llmGateway;
    private final LlmModelResolver llmModelResolver;
    private final KnowledgeSearchService knowledgeSearchService;
    private final QueryRewriteService queryRewriteService;
    private final RagAnswerService ragAnswerService;
    private final Nl2SqlService nl2SqlService;
    private final RequirementServiceImpl requirementService;
    private final Executor sseExecutor;

    public AssistantServiceImpl(AssistantSessionMapper sessionMapper,
                                AssistantMessageMapper messageMapper,
                                QuestionLogMapper questionLogMapper,
                                AssistantOperationCatalogService catalogService,
                                AssistantActionValidator actionValidator,
                                LlmGateway llmGateway,
                                LlmModelResolver llmModelResolver,
                                KnowledgeSearchService knowledgeSearchService,
                                QueryRewriteService queryRewriteService,
                                RagAnswerService ragAnswerService,
                                Nl2SqlService nl2SqlService,
                                RequirementServiceImpl requirementService,
                                @Qualifier(SseExecutorConfig.SSE_TASK_EXECUTOR) Executor sseExecutor) {
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.questionLogMapper = questionLogMapper;
        this.catalogService = catalogService;
        this.actionValidator = actionValidator;
        this.llmGateway = llmGateway;
        this.llmModelResolver = llmModelResolver;
        this.knowledgeSearchService = knowledgeSearchService;
        this.queryRewriteService = queryRewriteService;
        this.ragAnswerService = ragAnswerService;
        this.nl2SqlService = nl2SqlService;
        this.requirementService = requirementService;
        this.sseExecutor = sseExecutor;
    }

    @Override
    public List<AssistantSessionVO> listSessions() {
        Long userId = requireCurrentUserId();
        List<AssistantSession> sessions = sessionMapper.selectList(
                new LambdaQueryWrapper<AssistantSession>()
                        .eq(AssistantSession::getUserId, userId)
                        .orderByDesc(AssistantSession::getUpdatedAt)
        );
        if (sessions.isEmpty()) {
            return List.of();
        }
        // 一次批量取每个会话的最新消息，避免逐会话查询
        List<Long> sessionIds = sessions.stream().map(AssistantSession::getId).toList();
        Map<Long, AssistantMessage> latestBySession = messageMapper.selectLatestBySessionIds(sessionIds).stream()
                .collect(java.util.stream.Collectors.toMap(AssistantMessage::getSessionId, m -> m, (a, b) -> a));

        List<AssistantSessionVO> result = new ArrayList<>();
        for (AssistantSession session : sessions) {
            AssistantSessionVO vo = toSessionVO(session);
            AssistantMessage latestMessage = latestBySession.get(session.getId());
            if (latestMessage != null) {
                vo.setLastMessageAt(latestMessage.getCreatedAt());
                vo.setLastMessagePreview(buildPreview(latestMessage.getContent()));
            }
            result.add(vo);
        }
        return result;
    }

    @Override
    public AssistantSessionVO createSession(AssistantSessionCreateDTO createDTO) {
        Long userId = requireCurrentUserId();
        AssistantSession session = new AssistantSession();
        session.setUserId(userId);
        session.setTitle(resolveSessionTitle(createDTO != null ? createDTO.getTitle() : null));
        sessionMapper.insert(session);
        return toSessionVO(session);
    }

    @Override
    public List<AssistantMessageVO> listMessages(Long sessionId) {
        assertSessionOwnedByCurrentUser(sessionId);
        List<AssistantMessage> messages = messageMapper.selectList(
                new LambdaQueryWrapper<AssistantMessage>()
                        .eq(AssistantMessage::getSessionId, sessionId)
                        .orderByAsc(AssistantMessage::getId)
        );
        return messages.stream().map(this::toMessageVO).toList();
    }

    @Override
    public void deleteSession(Long sessionId) {
        assertSessionOwnedByCurrentUser(sessionId);
        sessionMapper.deleteById(sessionId);
        messageMapper.delete(new LambdaQueryWrapper<AssistantMessage>().eq(AssistantMessage::getSessionId, sessionId));
    }

    @Override
    public SseEmitter streamMessage(Long sessionId, AssistantChatRequest request) {
        Long userId = requireCurrentUserId();
        boolean superAdmin = SecurityUtils.isSuperAdmin();
        List<String> permissions = SecurityUtils.getCurrentUserPermissions();
        AssistantSession session = assertSessionOwnedByCurrentUser(sessionId);

        String userContent = request.getMessage().trim();
        AssistantMessage userMessage = new AssistantMessage();
        userMessage.setSessionId(sessionId);
        userMessage.setUserId(userId);
        userMessage.setRole("user");
        userMessage.setContent(userContent);
        userMessage.setStatus("completed");
        userMessage.setPageContext(request.getPageContext());
        messageMapper.insert(userMessage);

        // 埋点：记录用户提问到 question_logs，回答成功/失败后回写 answered，供 AI 提炼高频问题
        Long questionLogId = recordQuestionLog(userId, request, userContent, sessionId);

        AssistantMessage assistantMessage = new AssistantMessage();
        assistantMessage.setSessionId(sessionId);
        assistantMessage.setUserId(userId);
        assistantMessage.setRole("assistant");
        assistantMessage.setContent("");
        assistantMessage.setStatus("streaming");
        assistantMessage.setPageContext(request.getPageContext());
        messageMapper.insert(assistantMessage);

        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        dispatchByIntent(emitter, request, userContent, userMessage.getId(), assistantMessage, session,
                questionLogId, superAdmin, permissions, false);
        return emitter;
    }

    @Override
    public SseEmitter regenerateMessage(Long sessionId, AssistantRegenerateRequest request) {
        Long userId = requireCurrentUserId();
        boolean superAdmin = SecurityUtils.isSuperAdmin();
        List<String> permissions = SecurityUtils.getCurrentUserPermissions();
        AssistantSession session = assertSessionOwnedByCurrentUser(sessionId);

        AssistantMessage oldAssistant = messageMapper.selectById(request.getAssistantMessageId());
        if (oldAssistant == null
                || !Objects.equals(oldAssistant.getSessionId(), sessionId)
                || !Objects.equals(oldAssistant.getUserId(), userId)
                || !"assistant".equals(oldAssistant.getRole())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "消息不存在或无权访问");
        }

        // 找到该回复对应的上一条用户问题
        AssistantMessage userMessage = messageMapper.selectOne(
                new LambdaQueryWrapper<AssistantMessage>()
                        .eq(AssistantMessage::getSessionId, sessionId)
                        .eq(AssistantMessage::getRole, "user")
                        .lt(AssistantMessage::getId, oldAssistant.getId())
                        .orderByDesc(AssistantMessage::getId)
                        .last("LIMIT 1")
        );
        if (userMessage == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "未找到对应的用户问题");
        }

        // 旧回复作废（逻辑删除），重新生成一条新回复
        messageMapper.deleteById(oldAssistant.getId());

        AssistantMessage newAssistant = new AssistantMessage();
        newAssistant.setSessionId(sessionId);
        newAssistant.setUserId(userId);
        newAssistant.setRole("assistant");
        newAssistant.setContent("");
        newAssistant.setStatus("streaming");
        newAssistant.setPageContext(request.getPageContext() != null ? request.getPageContext() : oldAssistant.getPageContext());
        messageMapper.insert(newAssistant);

        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        AssistantChatRequest unifiedRequest = toChatRequest(request);
        dispatchByIntent(emitter, unifiedRequest, userMessage.getContent(), userMessage.getId(), newAssistant, session,
                null, superAdmin, permissions, true);
        return emitter;
    }

    /** 按请求意图分发到数据问答 / 知识库问答 / 联网搜索 / 通用操作导航四条流式链路 */
    private void dispatchByIntent(SseEmitter emitter,
                                  AssistantChatRequest request,
                                  String userContent,
                                  Long userMessageId,
                                  AssistantMessage assistantMessage,
                                  AssistantSession session,
                                  Long questionLogId,
                                  boolean superAdmin,
                                  List<String> permissions,
                                  boolean regenerate) {
        boolean explicitDataQuery = Boolean.TRUE.equals(request.getDataQuery());
        boolean autoDataQuery = request.getDataQuery() == null
                && nl2SqlService.isEnabled()
                && request.getKnowledgeBaseId() == null
                && !Boolean.TRUE.equals(request.getWebSearch())
                && !hasExplicitSearchScope(request)
                && nl2SqlService.looksLikeDataQuery(userContent);
        if (explicitDataQuery || autoDataQuery) {
            // 数据问答分支（NL2SQL）：自然语言 → 只读 SQL → 数据库 → 整合智能回答
            assistantMessage.setIntent("data_query");
            messageMapper.updateById(assistantMessage);
            if (!regenerate) {
                updateSessionAfterInteraction(session, buildPreview(userContent));
            }
            CompletableFuture.runAsync(() -> doStreamDataQueryReply(
                    emitter, request, userContent, userMessageId, assistantMessage,
                    questionLogId, superAdmin, permissions), sseExecutor);
        } else if (request.getKnowledgeBaseId() != null
                || hasExplicitSearchScopeValue(request, KnowledgeSearchScope.REQUIREMENT_BODY)
                || hasExplicitSearchScopeValue(request, KnowledgeSearchScope.KNOWLEDGE_BASE)) {
            // 知识库检索问答分支：跨全部或指定知识库做 RAG 问答
            assistantMessage.setIntent("knowledge_qa");
            messageMapper.updateById(assistantMessage);
            if (!regenerate) {
                updateSessionAfterInteraction(session, buildPreview(userContent));
            }
            CompletableFuture.runAsync(() -> doStreamKnowledgeReply(
                    emitter, request, userContent, userMessageId, assistantMessage, questionLogId), sseExecutor);
        } else if (Boolean.TRUE.equals(request.getWebSearch())
                || hasExplicitSearchScopeValue(request, KnowledgeSearchScope.WEB)) {
            // 联网搜索分支：轻量检索本地知识库 + LLM 联网搜索综合回答
            assistantMessage.setIntent("web_search");
            messageMapper.updateById(assistantMessage);
            if (!regenerate) {
                updateSessionAfterInteraction(session, buildPreview(userContent));
            }
            CompletableFuture.runAsync(() -> doStreamWebSearchReply(
                    emitter, request, userContent, userMessageId, assistantMessage, questionLogId), sseExecutor);
        } else {
            // 通用操作导航分支
            AssistantOperationAdvice advice = catalogService.advise(userContent, request.getPageContext(), permissions, superAdmin);
            List<AssistantAction> validatedActions = actionValidator.sanitize(advice.getActions(), permissions, superAdmin);
            advice.setActions(validatedActions);
            assistantMessage.setIntent(advice.getIntent());
            assistantMessage.setActions(validatedActions);
            assistantMessage.setSources(advice.getSources());
            messageMapper.updateById(assistantMessage);
            if (!regenerate) {
                updateSessionAfterInteraction(session, advice.getSessionTitle());
            }
            CompletableFuture.runAsync(() -> doStreamReply(
                    emitter, advice, userContent, request.getPageContext(), userMessageId, assistantMessage,
                    request.getLlmModelId(), request.getFiles(), questionLogId), sseExecutor);
        }
    }

    /** 重新生成请求复用统一的聊天请求结构 */
    private AssistantChatRequest toChatRequest(AssistantRegenerateRequest request) {
        AssistantChatRequest chatRequest = new AssistantChatRequest();
        chatRequest.setMessage(request.getMessage());
        chatRequest.setPageContext(request.getPageContext());
        chatRequest.setKnowledgeBaseId(request.getKnowledgeBaseId());
        chatRequest.setLlmModelId(request.getLlmModelId());
        chatRequest.setMode(request.getMode());
        chatRequest.setTopK(request.getTopK());
        chatRequest.setWebSearch(request.getWebSearch());
        chatRequest.setSearchScopes(request.getSearchScopes());
        chatRequest.setFiles(request.getFiles());
        chatRequest.setHistory(request.getHistory());
        chatRequest.setDataQuery(request.getDataQuery());
        return chatRequest;
    }

    private void doStreamReply(SseEmitter emitter,
                               AssistantOperationAdvice advice,
                               String userMessage,
                               com.demand.system.module.assistant.dto.AssistantPageContext pageContext,
                               Long userMessageId,
                               AssistantMessage assistantMessage,
                               Long llmModelId,
                               List<AssistantFileAttachment> files,
                               Long questionLogId) {
        StringBuilder answer = new StringBuilder();
        StringBuilder reasoning = new StringBuilder();

        // 文件上下文注入用户消息
        String fileContext = buildFileContext(files);
        String enhancedMessage = fileContext.isEmpty() ? userMessage : fileContext + "\n\n用户问题：" + userMessage;

        // ===== 任务节点 =====
        List<AssistantTask> tasks = new ArrayList<>();
        AssistantTask contextTask = new AssistantTask("general_context", "理解上下文");
        AssistantTask llmTask = new AssistantTask("general_llm", "调用模型");
        tasks.add(contextTask);
        tasks.add(llmTask);

        try {
            emitter.send(SseEmitter.event().name("meta").data(Map.of(
                    "sessionId", assistantMessage.getSessionId(),
                    "userMessageId", userMessageId,
                    "assistantMessageId", assistantMessage.getId()
            )));
            emitter.send(SseEmitter.event().name("actions").data(Map.of(
                    "intent", advice.getIntent(),
                    "actions", advice.getActions(),
                    "sources", advice.getSources(),
                    "tasks", tasks
            )));

            // ===== Task 1: 理解上下文（注入页面 / 权限 / 文件）=====
            contextTask.start("融合当前页面、权限与上传文件上下文");
            pushTaskUpdate(emitter, contextTask);
            try {
                Thread.sleep(20); // 让前端能感知到 running 状态
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            contextTask.complete(files == null || files.isEmpty()
                    ? "上下文就绪"
                    : "上下文就绪（含 " + files.size() + " 个文件）");
            pushTaskUpdate(emitter, contextTask);

            ResolvedChatModel chatModel = resolveChatModel(llmModelId);
            boolean llmSucceeded = false;
            if (chatModel != null) {
                // ===== Task 2: 调用模型 =====
                String modelLabel = chatModel.provider.getModel() != null && !chatModel.provider.getModel().isBlank()
                        ? chatModel.provider.getModel()
                        : (chatModel.provider.getProtocol() != null ? chatModel.provider.getProtocol() : "LLM");
                llmTask.start("调用 " + modelLabel);
                pushTaskUpdate(emitter, llmTask);

                String systemPrompt = buildSystemPrompt(pageContext, advice);
                String finalFallbackAnswer = advice.getFallbackAnswer();
                Map<String, Object> thinkingParams = llmGateway.buildThinkingParams(chatModel.provider, chatModel.maxTokens);
                try {
                    llmGateway.streamChatWithProvider(
                            chatModel.provider,
                            systemPrompt,
                            buildUserPrompt(enhancedMessage, advice, finalFallbackAnswer),
                            chatModel.temperature,
                            chatModel.maxTokens,
                            thinkingParams,
                            token -> pushDelta(emitter, answer, token),
                            token -> pushReasoningDelta(emitter, reasoning, token),
                            usage -> applyTokenUsage(assistantMessage, usage)
                    );
                    llmSucceeded = answer.length() > 0 || reasoning.length() > 0;
                } catch (Exception llmError) {
                    // 深度思考参数不被端点支持时（如部分 anthropic 兼容端点）降级为普通模式重试一次
                    if (answer.length() == 0 && reasoning.length() == 0) {
                        log.warn("assistant llm stream failed with thinking params, retry without thinking, sessionId={}", assistantMessage.getSessionId(), llmError);
                        try {
                            llmGateway.streamChatWithProvider(
                                    chatModel.provider,
                                    systemPrompt,
                                    buildUserPrompt(enhancedMessage, advice, finalFallbackAnswer),
                                    chatModel.temperature,
                                    chatModel.maxTokens,
                                    null,
                                    token -> pushDelta(emitter, answer, token),
                                    token -> pushReasoningDelta(emitter, reasoning, token),
                                    usage -> applyTokenUsage(assistantMessage, usage)
                            );
                            llmSucceeded = answer.length() > 0 || reasoning.length() > 0;
                        } catch (Exception retryError) {
                            log.warn("assistant llm stream retry also failed, fallback to rule-based answer, sessionId={}", assistantMessage.getSessionId(), retryError);
                        }
                    } else {
                        log.warn("assistant llm stream failed mid-flight, keep partial output, sessionId={}", assistantMessage.getSessionId(), llmError);
                        llmSucceeded = true; // 部分输出也算成功
                    }
                }

                if (llmSucceeded) {
                    llmTask.complete("回答生成完成");
                } else {
                    llmTask.fail("LLM 生成失败，将使用预设回复");
                }
                pushTaskUpdate(emitter, llmTask);
            } else {
                llmTask.fail("无可用模型配置");
                pushTaskUpdate(emitter, llmTask);
            }

            if (answer.length() == 0) {
                streamFallbackText(emitter, answer, advice.getFallbackAnswer());
            }

            assistantMessage.setContent(answer.toString());
            assistantMessage.setReasoning(reasoning.length() > 0 ? reasoning.toString() : null);
            assistantMessage.setStatus("completed");
            assistantMessage.setActions(advice.getActions());
            assistantMessage.setSources(advice.getSources());
            assistantMessage.setIntent(advice.getIntent());
            messageMapper.updateById(assistantMessage);
            markQuestionAnswered(questionLogId, true);

            try {
                emitter.send(SseEmitter.event().name("done").data(toMessageVO(assistantMessage)));
            } catch (Exception doneSendError) {
                log.info("assistant stream done event send skipped, sessionId={}", assistantMessage.getSessionId(), doneSendError);
            }
            emitter.complete();
        } catch (Exception e) {
            log.warn("assistant stream failed, sessionId={}", assistantMessage.getSessionId(), e);
            if ("running".equals(contextTask.getStatus())) {
                contextTask.fail("上下文处理异常");
                pushTaskUpdate(emitter, contextTask);
            }
            if ("running".equals(llmTask.getStatus())) {
                llmTask.fail("LLM 调用异常：" + e.getMessage());
                pushTaskUpdate(emitter, llmTask);
            }
            if (answer.length() == 0) {
                answer.append(advice.getFallbackAnswer());
            }
            assistantMessage.setContent(answer.toString());
            assistantMessage.setReasoning(reasoning.length() > 0 ? reasoning.toString() : null);
            assistantMessage.setStatus("failed");
            assistantMessage.setActions(advice.getActions());
            assistantMessage.setSources(advice.getSources());
            assistantMessage.setIntent(advice.getIntent());
            messageMapper.updateById(assistantMessage);
            markQuestionAnswered(questionLogId, false);
            sendErrorAndComplete(emitter, e, "操作助手响应失败");
        }
    }

    /**
     * 数据问答流式回复（NL2SQL）：
     * <ol>
     *   <li>问题理解与 SQL 生成（LLM 严格 JSON 输出）</li>
     *   <li>安全校验（单条只读 SELECT + 表白名单）与数据权限 / 软删除过滤注入</li>
     *   <li>只读执行查询，先下发结构化结果（表格与图表），再逐字输出整合性回答</li>
     * </ol>
     * 当模型判定问题与数据库数据无关时，自动回退到通用操作导航链路，避免用户白问一次。
     */
    private void doStreamDataQueryReply(SseEmitter emitter,
                                        AssistantChatRequest request,
                                        String userMessage,
                                        Long userMessageId,
                                        AssistantMessage assistantMessage,
                                        Long questionLogId,
                                        boolean superAdmin,
                                        List<String> permissions) {
        StringBuilder answer = new StringBuilder();
        StringBuilder reasoning = new StringBuilder();
        List<AssistantTask> tasks = new ArrayList<>();
        try {
            emitter.send(SseEmitter.event().name("meta").data(Map.of(
                    "sessionId", assistantMessage.getSessionId(),
                    "userMessageId", userMessageId,
                    "assistantMessageId", assistantMessage.getId()
            )));

            Long userId = assistantMessage.getUserId();
            // 数据权限范围由后端按登录态计算，绝不信任前端传入
            List<Long> visibleOrgIds = requirementService.resolveVisibleOrgIds(userId, superAdmin);
            Nl2SqlContext context = new Nl2SqlContext(
                    userMessage,
                    request.getHistory(),
                    request.getLlmModelId(),
                    userId,
                    superAdmin,
                    visibleOrgIds,
                    request.getPageContext());

            Nl2SqlOutcome outcome = nl2SqlService.query(context,
                    task -> {
                        mergeTask(tasks, task);
                        pushTaskUpdate(emitter, task);
                    },
                    new Nl2SqlAnswerStream() {
                        @Override
                        public void onDataResult(DataQueryResult result) {
                            sendDataResult(emitter, result);
                        }

                        @Override
                        public void onToken(String token) {
                            pushDelta(emitter, answer, token);
                        }

                        @Override
                        public void onReasoning(String token) {
                            pushReasoningDelta(emitter, reasoning, token);
                        }

                        @Override
                        public void onUsage(LlmGateway.ChatUsage usage) {
                            applyTokenUsage(assistantMessage, usage);
                        }
                    });

            if (!outcome.isDataQuery()) {
                // 判定为非数据问题：回退到通用操作导航链路（同一 emitter 内继续，不重复建立连接）
                log.info("assistant data query degraded to navigation, sessionId={}", assistantMessage.getSessionId());
                AssistantOperationAdvice advice = catalogService.advise(
                        userMessage, request.getPageContext(), permissions, superAdmin);
                List<AssistantAction> validatedActions =
                        actionValidator.sanitize(advice.getActions(), permissions, superAdmin);
                advice.setActions(validatedActions);
                doStreamReply(emitter, advice, userMessage, request.getPageContext(), userMessageId,
                        assistantMessage, request.getLlmModelId(), request.getFiles(), questionLogId);
                return;
            }

            // 失败时若链路上没有任何文字下发给用户，必须补一条可读说明：
            // 否则前端只会看到任务面板变红、对话区一片空白，用户不知道发生了什么。
            if (!outcome.success() && answer.length() == 0) {
                String reason = outcome.errorMessage() == null || outcome.errorMessage().isBlank()
                        ? "未知原因"
                        : outcome.errorMessage().trim();
                // 上游异常信息常自带句末标点，避免拼接出"。。"
                while (reason.endsWith("。") || reason.endsWith(".") || reason.endsWith("；")) {
                    reason = reason.substring(0, reason.length() - 1);
                }
                pushDelta(emitter, answer, "数据问答暂时不可用：" + reason
                        + "。可以稍后重试；若持续失败，请联系管理员检查"
                        + "【系统设置 → 模型配置】中模型的额度与可用性。");
            }

            assistantMessage.setDataResult(outcome.dataResult());
            assistantMessage.setTasks(tasks);
            assistantMessage.setIntent("data_query");
            if (outcome.warnings() != null && !outcome.warnings().isEmpty()) {
                assistantMessage.setWarnings(outcome.warnings());
            }
            if (reasoning.length() > 0) {
                assistantMessage.setReasoning(reasoning.toString());
            }
            assistantMessage.setContent(answer.toString());
            assistantMessage.setStatus(outcome.success() ? "completed" : "failed");
            messageMapper.updateById(assistantMessage);
            markQuestionAnswered(questionLogId, outcome.success());

            emitter.send(SseEmitter.event().name("actions").data(Map.of(
                    "intent", "data_query",
                    "actions", List.of(),
                    "sources", List.of(),
                    "tasks", tasks,
                    "warnings", assistantMessage.getWarnings() == null ? List.of() : assistantMessage.getWarnings()
            )));
            emitter.send(SseEmitter.event().name("done").data(toMessageVO(assistantMessage)));
            emitter.complete();
        } catch (Exception e) {
            log.warn("assistant data query stream failed, sessionId={}", assistantMessage.getSessionId(), e);
            String fallback = "数据查询失败：" + (e.getMessage() != null ? e.getMessage() : "请稍后重试");
            if (answer.length() == 0) {
                answer.append(fallback);
            }
            assistantMessage.setContent(answer.toString());
            assistantMessage.setReasoning(reasoning.length() > 0 ? reasoning.toString() : null);
            assistantMessage.setStatus("failed");
            assistantMessage.setIntent("data_query");
            assistantMessage.setTasks(tasks);
            messageMapper.updateById(assistantMessage);
            markQuestionAnswered(questionLogId, false);
            sendErrorAndComplete(emitter, e, fallback);
        }
    }

    /** 合并任务节点（同 id 覆盖），保证 SSE 与最终消息体一致 */
    private void mergeTask(List<AssistantTask> tasks, AssistantTask task) {
        if (task == null) {
            return;
        }
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
    }

    /** 下发结构化数据结果；发送失败（如客户端已断开）不影响主流程 */
    private void sendDataResult(SseEmitter emitter, DataQueryResult result) {
        if (result == null) {
            return;
        }
        try {
            emitter.send(SseEmitter.event().name("dataResult").data(result));
        } catch (Exception e) {
            log.debug("下发数据结果事件失败（客户端可能已断开）", e);
        }
    }

    /**
     * 知识库检索问答流式回复（真流式）：
     * <ol>
     *   <li>问题解析：结合多轮历史做查询改写，产出独立检索问题、关键词与追问推荐</li>
     *   <li>文档检索：改写后的问题做向量+关键词混合检索（仅检索，不生成）</li>
     *   <li>生成回答：基于命中的片段流式生成（深度思考与真实 token 用量同步推送）</li>
     * </ol>
     * 命中片段但生成失败/无模型时，如实展示检索摘要与来源，不再误报"未找到"。
     */
    private void doStreamKnowledgeReply(SseEmitter emitter,
                                        AssistantChatRequest request,
                                        String userMessage,
                                        Long userMessageId,
                                        AssistantMessage assistantMessage,
                                        Long questionLogId) {
        StringBuilder answer = new StringBuilder();
        List<AssistantTask> tasks = new ArrayList<>();
        try {
            emitter.send(SseEmitter.event().name("meta").data(Map.of(
                    "sessionId", assistantMessage.getSessionId(),
                    "userMessageId", userMessageId,
                    "assistantMessageId", assistantMessage.getId()
            )));

            Long rawKbId = request.getKnowledgeBaseId();
            Long searchKbId = (rawKbId != null && rawKbId == -1L) ? null : rawKbId;
            String mode = request.getMode() != null ? request.getMode() : "hybrid";
            int topK = request.getTopK() != null ? request.getTopK() : 10;

            // ===== Task 1: 问题解析（LLM 查询改写，失败自动降级）=====
            AssistantTask parseTask = new AssistantTask("query_parse", "问题解析");
            tasks.add(parseTask);
            parseTask.start("解析用户问题：「" + shorten(userMessage, 30) + "」");
            QueryRewriteResult rewrite;
            try {
                rewrite = queryRewriteService.rewrite(userMessage, request.getHistory());
                parseTask.log("info", rewrite.rewritten()
                        ? "改写为独立检索问题：" + shorten(rewrite.safeStandaloneQuery(), 40)
                        : "未启用改写，按原始问题检索");
                if (!rewrite.keywords().isEmpty()) {
                    parseTask.log("info", "检索关键词：" + String.join("、", rewrite.keywords()));
                }
                parseTask.complete("问题解析完成");
            } catch (Exception e) {
                rewrite = QueryRewriteResult.fallback(userMessage);
                parseTask.fail("问题解析异常：" + e.getMessage());
            }
            pushTaskUpdate(emitter, parseTask);

            // ===== Task 2: 文档检索 =====
            AssistantTask searchTask = new AssistantTask("retrieve", "文档检索");
            tasks.add(searchTask);
            searchTask.start("向量嵌入 + 知识库检索…");
            KnowledgeSearchResponse searchResponse;
            try {
                KnowledgeSearchRequest searchRequest = new KnowledgeSearchRequest();
                // 检索用改写后的独立问题；附件内容只注入生成阶段，避免长文本污染查询向量
                searchRequest.setQuery(rewrite.safeStandaloneQuery());
                searchRequest.setHistory(request.getHistory());
                searchRequest.setKnowledgeBaseId(searchKbId);
                searchRequest.setMode(mode);
                searchRequest.setTopK(topK);
                searchRequest.setLlmModelId(request.getLlmModelId());
                searchRequest.setRequesterId(assistantMessage.getUserId());
                searchRequest.setSearchScopes(resolveAssistantSearchScopes(request, false));

                String kbDesc = searchKbId != null ? "指定知识库 ID=" + searchKbId : "全部知识库";
                searchTask.log("info", "检索范围：" + kbDesc + "，模式：" + mode + "，TopK：" + topK);

                searchResponse = knowledgeSearchService.retrieve(searchRequest);

                int resultCount = searchResponse.getResults().size();
                int uniqueDocs = (int) searchResponse.getResults().stream()
                        .map(KnowledgeSearchResponse.SearchResultItem::getDocumentId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .count();
                searchTask.log("info", "检索完成：找到 " + resultCount + " 条相关片段，来自 " + uniqueDocs + " 份文档");
                if ("hybrid".equals(mode)) {
                    boolean degraded = searchResponse.getWarnings() != null && searchResponse.getWarnings().stream()
                            .anyMatch(w -> w.contains("重排序模型暂不可用"));
                    searchTask.log("info", degraded
                            ? "重排序模型暂不可用，已按基础相关性排序"
                            : "Reranker 已对结果进行重排序");
                }
                searchTask.complete("检索到 " + resultCount + " 条片段（" + uniqueDocs + " 份文档）");
            } catch (Exception e) {
                searchTask.fail("文档检索失败：" + e.getMessage());
                pushTaskUpdate(emitter, searchTask);
                throw e;
            }
            pushTaskUpdate(emitter, searchTask);

            // ===== 组装来源与检索元信息 =====
            List<AssistantSource> sources = mapCitationsToSources(searchResponse.getCitations(), rawKbId);
            assistantMessage.setSources(sources);
            assistantMessage.setWarnings(searchResponse.getWarnings());
            assistantMessage.setIntent("knowledge_qa");
            assistantMessage.setTasks(tasks);
            assistantMessage.setThinkingSteps(searchResponse.getThinkingSteps());
            assistantMessage.setProcessSummary(searchResponse.getProcessSummary());
            assistantMessage.setRetrievedCount(searchResponse.getResults().size());
            assistantMessage.setCitations(searchResponse.getCitations());
            assistantMessage.setSuggestedFollowUps(rewrite.followUps());

            emitter.send(SseEmitter.event().name("actions").data(Map.of(
                    "intent", "knowledge_qa",
                    "actions", List.of(),
                    "sources", sources,
                    "tasks", tasks,
                    "warnings", searchResponse.getWarnings() == null ? List.of() : searchResponse.getWarnings()
            )));

            // ===== Task 3: 生成回答（真流式；命中即有产出，不再误报“未找到”）=====
            AssistantTask generateTask = new AssistantTask("synthesize", "生成回答");
            tasks.add(generateTask);
            StringBuilder reasoning = new StringBuilder();
            boolean hasResults = !searchResponse.getResults().isEmpty();
            if (!hasResults) {
                generateTask.start("未命中相关文档，返回引导提示");
                streamFallbackText(emitter, answer, NO_RESULT_HINT);
                generateTask.complete("已返回提示信息");
                pushTaskUpdate(emitter, generateTask);
            } else {
                // 检索用改写后的独立问题；回答用用户原始问题 + 附件上下文，紧扣用户原意
                String fileContext = buildFileContext(request.getFiles());
                String answerQuery = fileContext.isEmpty()
                        ? userMessage
                        : userMessage + "\n\n" + fileContext;
                generateTask.start("调用 LLM 基于命中片段流式生成回答…");
                pushTaskUpdate(emitter, generateTask);
                try {
                    ragAnswerService.streamAnswerWithReasoning(
                            answerQuery,
                            searchResponse.getResults(),
                            searchKbId,
                            request.getLlmModelId(),
                            request.getHistory(),
                            token -> pushDelta(emitter, answer, token),
                            token -> pushReasoningDelta(emitter, reasoning, token),
                            usage -> applyTokenUsage(assistantMessage, usage)
                    );
                    generateTask.log("info", "回答长度：" + answer.length() + " 字");
                    generateTask.complete("回答生成完成");
                } catch (Exception generateError) {
                    // 生成失败但检索有果：如实展示检索摘要，不谎报“未找到”
                    log.warn("assistant knowledge qa generation failed, fallback to retrieval summary, sessionId={}",
                            assistantMessage.getSessionId(), generateError);
                    generateTask.log("warn", "LLM 生成失败：" + generateError.getMessage() + "，已展示检索摘要");
                    if (answer.length() == 0) {
                        streamFallbackText(emitter, answer, buildRetrievalSummary(searchResponse));
                    }
                    generateTask.complete("已返回检索摘要（生成失败降级）");
                }
                pushTaskUpdate(emitter, generateTask);
            }
            if (reasoning.length() > 0) {
                assistantMessage.setReasoning(reasoning.toString());
            }

            assistantMessage.setContent(answer.toString());
            assistantMessage.setStatus("completed");
            messageMapper.updateById(assistantMessage);
            markQuestionAnswered(questionLogId, true);

            emitter.send(SseEmitter.event().name("done").data(toMessageVO(assistantMessage)));
            emitter.complete();
        } catch (Exception e) {
            log.warn("assistant knowledge qa stream failed, sessionId={}", assistantMessage.getSessionId(), e);
            String fallback = "知识库检索问答失败：" + (e.getMessage() != null ? e.getMessage() : "请稍后重试");
            if (answer.length() == 0) {
                answer.append(fallback);
            }
            assistantMessage.setContent(answer.toString());
            assistantMessage.setStatus("failed");
            assistantMessage.setIntent("knowledge_qa");
            assistantMessage.setTasks(tasks);
            messageMapper.updateById(assistantMessage);
            markQuestionAnswered(questionLogId, false);
            sendErrorAndComplete(emitter, e, fallback);
        }
    }

    /** 生成失败但检索命中时的兜底摘要：如实列出已命中的片段与来源 */
    private String buildRetrievalSummary(KnowledgeSearchResponse searchResponse) {
        List<KnowledgeSearchResponse.SearchResultItem> results = searchResponse.getResults();
        int uniqueDocs = (int) results.stream()
                .map(KnowledgeSearchResponse.SearchResultItem::getDocumentId)
                .filter(Objects::nonNull)
                .distinct()
                .count();
        StringBuilder sb = new StringBuilder();
        sb.append("已在知识库中找到 ").append(results.size()).append(" 条相关片段（来自 ")
          .append(uniqueDocs).append(" 份文档），但回答生成暂时失败，可参考下方来源，或稍后重试。\n");
        int limit = Math.min(results.size(), 5);
        for (int i = 0; i < limit; i++) {
            KnowledgeSearchResponse.SearchResultItem item = results.get(i);
            sb.append(i + 1).append(". ")
              .append(item.getFileName() != null ? item.getFileName() : "未知文档");
            if (item.getSectionTitle() != null && !item.getSectionTitle().isBlank()) {
                sb.append(" · ").append(item.getSectionTitle());
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private void sendErrorAndComplete(SseEmitter emitter, Exception e, String fallback) {
        try {
            emitter.send(SseEmitter.event().name("error").data(Map.of("message", fallback)));
        } catch (Exception ex) {
            log.debug("SSE 发送 error 事件失败(客户端可能已断开)", ex);
        }
        emitter.completeWithError(e);
    }

    /** 联网搜索 + 本地知识库整合的流式回复 */
    private void doStreamWebSearchReply(SseEmitter emitter,
                                        AssistantChatRequest request,
                                        String userMessage,
                                        Long userMessageId,
                                        AssistantMessage assistantMessage,
                                        Long questionLogId) {
        StringBuilder answer = new StringBuilder();
        StringBuilder reasoning = new StringBuilder();
        List<AssistantTask> tasks = new ArrayList<>();
        try {
            emitter.send(SseEmitter.event().name("meta").data(Map.of(
                    "sessionId", assistantMessage.getSessionId(),
                    "userMessageId", userMessageId,
                    "assistantMessageId", assistantMessage.getId()
            )));

            // ===== Task 1: 问题解析（含查询改写）=====
            AssistantTask parseTask = new AssistantTask("query_parse", "问题解析");
            tasks.add(parseTask);
            parseTask.start("解析用户问题：「" + shorten(userMessage, 30) + "」");
            QueryRewriteResult rewrite;
            try {
                rewrite = queryRewriteService.rewrite(userMessage, request.getHistory());
                if (!rewrite.keywords().isEmpty()) {
                    parseTask.log("info", "检索关键词：" + String.join("、", rewrite.keywords()));
                }
                parseTask.complete("问题解析完成");
            } catch (Exception e) {
                rewrite = QueryRewriteResult.fallback(userMessage);
                parseTask.complete("问题解析完成（未启用改写）");
            }
            pushTaskUpdate(emitter, parseTask);

            // ===== Task 2: 轻量检索全部本地知识库 =====
            AssistantTask kbTask = new AssistantTask("retrieve", "本地知识库检索");
            tasks.add(kbTask);
            String kbContext = "";
            List<AssistantSource> kbSources = new ArrayList<>();
            List<KnowledgeSearchResponse.ThinkingStep> thinkingSteps = new ArrayList<>();
            int retrievedCount = 0;
            List<String> retrievalWarnings = new ArrayList<>();
            kbTask.start("轻量检索全部本地知识库（topK=5）…");
            try {
                KnowledgeSearchRequest searchRequest = new KnowledgeSearchRequest();
                searchRequest.setQuery(rewrite.safeStandaloneQuery());
                searchRequest.setKnowledgeBaseId(null); // null = 全部知识库
                searchRequest.setMode("hybrid");
                searchRequest.setTopK(5); // 轻量检索，避免拖慢响应
                searchRequest.setLlmModelId(request.getLlmModelId());
                searchRequest.setRequesterId(assistantMessage.getUserId());
                searchRequest.setSearchScopes(resolveAssistantSearchScopes(request, true));

                KnowledgeSearchResponse searchResponse = knowledgeSearchService.retrieve(searchRequest);

                if (searchResponse.getResults() != null && !searchResponse.getResults().isEmpty()) {
                    StringBuilder ctx = new StringBuilder();
                    int idx = 0;
                    for (KnowledgeSearchResponse.SearchResultItem item : searchResponse.getResults()) {
                        idx++;
                        ctx.append("[本地知识库片段 ").append(idx).append("] 来源：《")
                           .append(item.getFileName() != null ? item.getFileName() : "未知文档").append("》\n")
                           .append(item.getContent() != null ? item.getContent() : "")
                           .append("\n\n");
                    }
                    kbContext = ctx.toString();
                    retrievedCount = searchResponse.getResults().size();
                    kbSources = mapCitationsToSources(searchResponse.getCitations(), null);
                    if (searchResponse.getWarnings() != null) {
                        retrievalWarnings.addAll(searchResponse.getWarnings());
                    }
                    kbTask.log("info", "命中 " + retrievedCount + " 条片段");
                } else {
                    kbTask.log("info", "本地知识库未命中相关片段");
                }
                if (searchResponse.getThinkingSteps() != null) {
                    thinkingSteps.addAll(searchResponse.getThinkingSteps());
                }
                kbTask.complete(retrievedCount > 0 ? "已获取 " + retrievedCount + " 条本地参考" : "本地知识库无命中（将继续联网搜索）");
                pushTaskUpdate(emitter, kbTask);
            } catch (Exception kbError) {
                log.warn("web search mode: local KB retrieval failed, sessionId={}", assistantMessage.getSessionId(), kbError);
                kbTask.log("warn", "本地检索异常：" + kbError.getMessage());
                kbTask.complete("本地检索跳过（异常降级）");
                pushTaskUpdate(emitter, kbTask);
            }

            // ===== Task 3: 联网搜索 =====
            AssistantTask webTask = new AssistantTask("web_search", "联网搜索");
            tasks.add(webTask);
            webTask.start("调用 LLM 联网搜索…");
            webTask.log("info", "已启用大模型联网搜索，将实时获取互联网信息" + (retrievedCount > 0 ? "，并结合本地知识库 " + retrievedCount + " 个片段" : ""));

            List<AssistantSource> allSources = new ArrayList<>(kbSources);
            AssistantSource webSource = new AssistantSource();
            webSource.setCode("web_search");
            webSource.setTitle("联网搜索");
            webSource.setReason("已启用大模型联网搜索，获取实时互联网信息");
            allSources.add(webSource);

            assistantMessage.setSources(allSources);
            assistantMessage.setIntent("web_search");
            assistantMessage.setThinkingSteps(thinkingSteps);
            assistantMessage.setTasks(tasks);
            assistantMessage.setRetrievedCount(retrievedCount);
            assistantMessage.setWarnings(retrievalWarnings);
            assistantMessage.setSuggestedFollowUps(rewrite.followUps());

            emitter.send(SseEmitter.event().name("actions").data(Map.of(
                    "intent", "web_search",
                    "actions", List.of(),
                    "sources", allSources,
                    "tasks", tasks,
                    "warnings", retrievalWarnings
            )));

            // ===== 调用 LLM 联网搜索流式回答 =====
            ResolvedChatModel chatModel = resolveChatModel(request.getLlmModelId());
            if (chatModel != null) {
                String systemPrompt = buildWebSearchSystemPrompt(request.getPageContext(), kbContext, retrievedCount);
                String fileContext = buildFileContext(request.getFiles());
                String userPrompt = buildWebSearchUserPrompt(userMessage);
                if (!fileContext.isEmpty()) {
                    userPrompt = fileContext + "\n" + userPrompt;
                }
                webTask.log("info", "模型：" + chatModel.provider.getProtocol() + " / " + chatModel.provider.getModel());
                Map<String, Object> webSearchParams = llmGateway.buildWebSearchParams(chatModel.provider);
                try {
                    llmGateway.streamChatWithProvider(
                            chatModel.provider,
                            systemPrompt,
                            userPrompt,
                            chatModel.temperature,
                            chatModel.maxTokens,
                            webSearchParams,
                            token -> pushDelta(emitter, answer, token),
                            token -> pushReasoningDelta(emitter, reasoning, token),
                            usage -> applyTokenUsage(assistantMessage, usage)
                    );
                    webTask.log("info", "回答长度：" + answer.length() + " 字");
                    webTask.complete("联网搜索完成");
                    pushTaskUpdate(emitter, webTask);
                } catch (Exception llmError) {
                    log.warn("web search llm stream failed, retry without web search params, sessionId={}", assistantMessage.getSessionId(), llmError);
                    webTask.log("warn", "联网搜索失败，降级为普通对话重试…");
                    if (answer.length() == 0 && reasoning.length() == 0) {
                        try {
                            llmGateway.streamChatWithProvider(
                                    chatModel.provider,
                                    systemPrompt,
                                    userPrompt,
                                    chatModel.temperature,
                                    chatModel.maxTokens,
                                    null,
                                    token -> pushDelta(emitter, answer, token),
                                    token -> pushReasoningDelta(emitter, reasoning, token),
                                    usage -> applyTokenUsage(assistantMessage, usage)
                            );
                            webTask.log("info", "降级重试成功，回答长度：" + answer.length() + " 字");
                            webTask.complete("降级重试完成");
                        } catch (Exception retryError) {
                            log.warn("web search retry without params also failed, sessionId={}", assistantMessage.getSessionId(), retryError);
                            webTask.fail("联网搜索与降级重试均失败");
                        }
                        pushTaskUpdate(emitter, webTask);
                    } else {
                        webTask.complete("联网搜索部分完成（已降级）");
                        pushTaskUpdate(emitter, webTask);
                    }
                }
            } else {
                webTask.fail("无可用 LLM 模型配置");
                pushTaskUpdate(emitter, webTask);
            }

            if (answer.length() == 0) {
                streamFallbackText(emitter, answer, "联网搜索暂时不可用，请稍后重试，或切换到知识库问答模式。");
            }

            assistantMessage.setContent(answer.toString());
            assistantMessage.setReasoning(reasoning.length() > 0 ? reasoning.toString() : null);
            assistantMessage.setStatus("completed");
            assistantMessage.setProcessSummary("联网搜索" + (retrievedCount > 0 ? " + 本地知识库(" + retrievedCount + "片段)" : ""));
            messageMapper.updateById(assistantMessage);
            markQuestionAnswered(questionLogId, true);

            emitter.send(SseEmitter.event().name("done").data(toMessageVO(assistantMessage)));
            emitter.complete();
        } catch (Exception e) {
            log.warn("assistant web search stream failed, sessionId={}", assistantMessage.getSessionId(), e);
            String fallback = "联网搜索失败：" + (e.getMessage() != null ? e.getMessage() : "请稍后重试");
            if (answer.length() == 0) {
                answer.append(fallback);
            }
            assistantMessage.setContent(answer.toString());
            assistantMessage.setReasoning(reasoning.length() > 0 ? reasoning.toString() : null);
            assistantMessage.setStatus("failed");
            assistantMessage.setIntent("web_search");
            messageMapper.updateById(assistantMessage);
            markQuestionAnswered(questionLogId, false);
            sendErrorAndComplete(emitter, e, fallback);
        }
    }

    /** 联网搜索模式的系统提示词 */
    private String buildWebSearchSystemPrompt(com.demand.system.module.assistant.dto.AssistantPageContext pageContext,
                                               String kbContext, int kbHitCount) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是企业需求系统中的通用AI助手，具备联网搜索能力。你可以实时搜索互联网获取最新信息，并结合本地知识库内容综合回答用户问题。\n")
                .append("回答要求：\n")
                .append("1. 优先利用联网搜索获取的实时信息回答问题，确保信息准确、时效性强。\n")
                .append("2. 若本地知识库有相关内容，应整合本地知识与联网信息，给出更全面的回答。\n")
                .append("3. 回答中涉及的事实性信息应注明来源（联网信息/本地知识库）。\n")
                .append("4. 语气简洁、专业，条理清晰，尽量分点说明。\n")
                .append("5. 如果问题涉及系统内操作（如新建需求、配置工作流等），仍可给出系统内页面入口建议。\n")
                .append("6. 不要输出 JSON。\n");
        if (pageContext != null) {
            prompt.append("当前页面上下文：route=")
                    .append(Objects.toString(pageContext.getRoute(), ""))
                    .append(", pageTitle=")
                    .append(Objects.toString(pageContext.getPageTitle(), ""))
                    .append(", activeMenu=")
                    .append(Objects.toString(pageContext.getActiveMenu(), ""))
                    .append("\n");
        }
        if (kbHitCount > 0 && kbContext != null && !kbContext.isBlank()) {
            prompt.append("\n以下是本地知识库检索到的相关内容，请在回答中参考整合：\n")
                  .append(kbContext)
                  .append("\n（本地知识库片段结束）\n");
        } else {
            prompt.append("\n本地知识库未检索到直接相关内容，请主要依赖联网搜索结果回答。\n");
        }
        return prompt.toString();
    }

    /** 联网搜索模式的用户提示词 */
    private String buildWebSearchUserPrompt(String userMessage) {
        return "用户问题：" + userMessage + "\n请结合联网搜索结果"
                + "（以及上方提供的本地知识库内容，如有）综合给出回答。";
    }

    /**
     * 将用户上传的文件附件列表转为可注入 LLM 提示词的文本上下文。
     * <p>文本类文件提取完整内容（上限 8000 字符），二进制文件仅标注文件名和大小。</p>
     */
    private String buildFileContext(List<AssistantFileAttachment> files) {
        if (files == null || files.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("【用户上传了文件】\n");
        for (int i = 0; i < files.size(); i++) {
            AssistantFileAttachment f = files.get(i);
            sb.append("\n[文件").append(i + 1).append("] ").append(f.getName())
              .append("（").append(formatFileSize(f.getSize())).append("，").append(f.getContentType()).append("）");
            if (f.getExtractedText() != null && !f.getExtractedText().isBlank()) {
                sb.append("\n文件内容：\n");
                int maxLen = 8000;
                String text = f.getExtractedText();
                if (text.length() > maxLen) {
                    text = text.substring(0, maxLen) + "\n…[已截断，剩余 " + (text.length() - maxLen) + " 字符]";
                }
                sb.append(text).append("\n");
            } else {
                sb.append("\n[此文件为非文本格式，内容未读取]\n");
            }
        }
        return sb.toString();
    }

    private String formatFileSize(Long bytes) {
        if (bytes == null) return "未知大小";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private boolean hasExplicitSearchScopeValue(AssistantChatRequest request, String scope) {
        return hasExplicitSearchScope(request) && request.getSearchScopes().stream()
                .filter(Objects::nonNull)
                .anyMatch(value -> scope.equalsIgnoreCase(value.trim()));
    }

    private boolean hasExplicitSearchScope(AssistantChatRequest request) {
        return request != null && request.getSearchScopes() != null && !request.getSearchScopes().isEmpty();
    }

    private List<String> resolveAssistantSearchScopes(AssistantChatRequest request, boolean webMode) {
        if (hasExplicitSearchScope(request)) {
            return request.getSearchScopes();
        }
        if (webMode) {
            return List.of(KnowledgeSearchScope.REQUIREMENT_BODY,
                    KnowledgeSearchScope.KNOWLEDGE_BASE, KnowledgeSearchScope.WEB);
        }
        return List.of(KnowledgeSearchScope.REQUIREMENT_BODY, KnowledgeSearchScope.KNOWLEDGE_BASE);
    }

    private List<AssistantSource> mapCitationsToSources(List<KnowledgeSearchResponse.CitationReference> citations, Long rawKbId) {
        if (citations == null || citations.isEmpty()) {
            return List.of();
        }
        List<AssistantSource> sources = new ArrayList<>();
        for (KnowledgeSearchResponse.CitationReference citation : citations) {
            AssistantSource source = new AssistantSource();
            boolean requirementBody = citation.getSourceType() != null
                    && citation.getSourceType().startsWith("requirement_body");
            source.setCode(requirementBody ? "requirement_body" : "knowledge_document");
            source.setSourceType(citation.getSourceType());
            source.setDocumentId(requirementBody ? null : citation.getDocumentId());
            source.setRequirementId(citation.getRequirementId());
            source.setRequirementNo(citation.getRequirementNo());
            source.setRequirementTitle(citation.getRequirementTitle());
            source.setContentType(citation.getContentType());
            source.setImageFileId(citation.getImageFileId());
            source.setImagePosition(citation.getImagePosition());
            source.setFocus(citation.getFocus());
            source.setTitle(requirementBody
                    ? ((citation.getRequirementNo() != null ? citation.getRequirementNo() + " " : "")
                    + (citation.getRequirementTitle() != null ? citation.getRequirementTitle() : "工单正文"))
                    : citation.getFileName());
            // 正文来源不进入附件预览链路；知识库附件仍携带知识库和文档标识。
            if (requirementBody) {
                source.setKnowledgeBaseId(null);
                source.setPath(citation.getRequirementId() == null ? null : "/requirements/" + citation.getRequirementId());
            } else {
                // 优先用 citation 自带的 knowledgeBaseId（跨库检索场景），否则用 rawKbId
                Long kbId = citation.getKnowledgeBaseId() != null
                        ? Long.valueOf(citation.getKnowledgeBaseId())
                        : (rawKbId != null && rawKbId != -1L ? rawKbId : null);
                source.setKnowledgeBaseId(kbId);
                source.setPath(rawKbId != null && rawKbId != -1L
                        ? "/settings/knowledge/" + rawKbId
                        : "/settings/knowledge");
            }
            int hitCount = citation.getHitCount() != null ? citation.getHitCount() : 0;
            double maxScore = citation.getMaxScore() != null ? citation.getMaxScore() : 0d;
            source.setHitCount(hitCount);
            source.setMaxScore(maxScore);
            String evidenceType = switch (citation.getContentType() == null ? "" : citation.getContentType()) {
                case "image_ocr" -> "，包含图片 OCR";
                case "image_caption" -> "，包含图片理解";
                case "body_image" -> "，包含正文图片 OCR/理解";
                default -> "";
            };
            source.setReason("命中 " + hitCount + " 个片段，相关度 " + Math.round(maxScore * 100) + "%" + evidenceType);
            sources.add(source);
        }
        return sources;
    }

    private void pushDelta(SseEmitter emitter, StringBuilder answer, String token) {
        if (token == null || token.isEmpty()) {
            return;
        }
        try {
            answer.append(token);
            emitter.send(SseEmitter.event().name("delta").data(token));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** 推送深度思考内容增量（reasoningDelta 事件），并累积到 reasoning StringBuilder */
    private void pushReasoningDelta(SseEmitter emitter, StringBuilder reasoning, String token) {
        if (token == null || token.isEmpty()) {
            return;
        }
        try {
            reasoning.append(token);
            emitter.send(SseEmitter.event().name("reasoningDelta").data(token));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** 推送单个任务状态更新（SSE taskUpdate 事件）。任务对象中包含当前状态和所有日志。 */
    private void pushTaskUpdate(SseEmitter emitter, AssistantTask task) {
        try {
            emitter.send(SseEmitter.event().name("taskUpdate").data(task));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** 将 LLM 返回的真实 token 用量写入消息实体 */
    private void applyTokenUsage(AssistantMessage assistantMessage, LlmGateway.ChatUsage usage) {
        if (usage == null) {
            return;
        }
        assistantMessage.setInputTokens(usage.getPromptTokens());
        assistantMessage.setOutputTokens(usage.getCompletionTokens());
        assistantMessage.setTotalTokens(usage.getTotalTokens());
    }

    private void streamFallbackText(SseEmitter emitter, StringBuilder answer, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        int chunkSize = 24;
        for (int index = 0; index < text.length(); index += chunkSize) {
            String token = text.substring(index, Math.min(index + chunkSize, text.length()));
            pushDelta(emitter, answer, token);
        }
    }

    private ResolvedChatModel resolveChatModel(Long llmModelId) {
        try {
            LlmModelResolver.ResolvedModel resolved = null;
            if (llmModelId != null) {
                resolved = llmModelResolver.resolveModel(llmModelId, LlmApplicationCode.ASSISTANT_CHAT);
            }
            if (resolved == null) {
                resolved = llmModelResolver.resolveFirst(LlmApplicationCode.ASSISTANT_CHAT);
            }
            if (resolved == null) {
                return null;
            }
            return new ResolvedChatModel(
                    llmModelResolver.toGatewayProvider(resolved),
                    resolved.model().getTemperature(),
                    resolved.model().getMaxTokens()
            );
        } catch (Exception e) {
            log.warn("assistant resolve chat model failed, fallback to rule-based answer", e);
            return null;
        }
    }

    private String buildSystemPrompt(com.demand.system.module.assistant.dto.AssistantPageContext pageContext,
                                     AssistantOperationAdvice advice) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是企业需求系统中的操作导航助手。回答目标是帮助用户找到正确页面入口和下一步操作，而不是替用户执行写操作。\n")
                .append("回答要求：\n")
                .append("1. 优先给出系统内可操作的页面入口与点击顺序。\n")
                .append("2. 只能基于已提供的动作建议回答，不要虚构系统不存在的菜单。\n")
                .append("3. 语气简洁、专业，尽量分点说明。\n")
                .append("4. 如果用户权限可能不足，要明确提醒联系管理员。\n")
                .append("5. 不要输出 JSON。\n");
        if (pageContext != null) {
            prompt.append("当前页面上下文：route=")
                    .append(Objects.toString(pageContext.getRoute(), ""))
                    .append(", pageTitle=")
                    .append(Objects.toString(pageContext.getPageTitle(), ""))
                    .append(", activeMenu=")
                    .append(Objects.toString(pageContext.getActiveMenu(), ""))
                    .append("\n");
        }
        prompt.append("允许推荐的动作：");
        if (advice.getActions() == null || advice.getActions().isEmpty()) {
            prompt.append("无明确导航动作，只能给出澄清式建议。\n");
        } else {
            for (AssistantAction action : advice.getActions()) {
                prompt.append("【")
                        .append(action.getLabel())
                        .append(" -> ")
                        .append(action.getTargetPath())
                        .append("】");
            }
            prompt.append("\n");
        }
        return prompt.toString();
    }

    private String buildUserPrompt(String userMessage, AssistantOperationAdvice advice, String fallbackAnswer) {
        StringBuilder builder = new StringBuilder();
        builder.append("用户问题：").append(userMessage).append("\n");
        builder.append("规则引擎建议：").append(fallbackAnswer).append("\n");
        if (advice.getActions() != null && !advice.getActions().isEmpty()) {
            builder.append("候选导航动作：\n");
            for (AssistantAction action : advice.getActions()) {
                builder.append("- ")
                        .append(action.getLabel())
                        .append("（")
                        .append(action.getTargetPath())
                        .append("）：")
                        .append(action.getDescription())
                        .append("\n");
            }
        }
        builder.append("请将规则建议整理成自然语言操作指引。\n");
        return builder.toString();
    }

    private void updateSessionAfterInteraction(AssistantSession session, String suggestedTitle) {
        AssistantSession update = new AssistantSession();
        update.setId(session.getId());
        String currentTitle = session.getTitle();
        if ((currentTitle == null || currentTitle.isBlank() || DEFAULT_SESSION_TITLE.equals(currentTitle))
                && suggestedTitle != null && !suggestedTitle.isBlank()) {
            update.setTitle(resolveSessionTitle(suggestedTitle));
        }
        update.setUpdatedAt(LocalDateTime.now());
        sessionMapper.updateById(update);
    }

    private AssistantSession assertSessionOwnedByCurrentUser(Long sessionId) {
        Long userId = requireCurrentUserId();
        AssistantSession session = sessionMapper.selectById(sessionId);
        if (session == null || !Objects.equals(session.getUserId(), userId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "会话不存在或无权访问");
        }
        return session;
    }

    private Long requireCurrentUserId() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "未登录或登录已过期");
        }
        return userId;
    }

    private AssistantSessionVO toSessionVO(AssistantSession session) {
        AssistantSessionVO vo = new AssistantSessionVO();
        vo.setId(session.getId());
        vo.setTitle(session.getTitle());
        vo.setCreatedAt(session.getCreatedAt());
        vo.setUpdatedAt(session.getUpdatedAt());
        return vo;
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
        return vo;
    }

    private String buildPreview(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        String compact = content.replaceAll("\s+", " ").trim();
        return compact.length() > 50 ? compact.substring(0, 50) + "…" : compact;
    }

    private String resolveSessionTitle(String title) {
        if (title == null || title.isBlank()) {
            return DEFAULT_SESSION_TITLE;
        }
        String compact = title.replaceAll("\s+", " ").trim();
        return compact.length() > 120 ? compact.substring(0, 120) : compact;
    }

    private String shorten(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "…";
    }

    private static class ResolvedChatModel {
        private final LlmGatewayConfig.Provider provider;
        private final java.math.BigDecimal temperature;
        private final Integer maxTokens;

        private ResolvedChatModel(LlmGatewayConfig.Provider provider, java.math.BigDecimal temperature, Integer maxTokens) {
            this.provider = provider;
            this.temperature = temperature;
            this.maxTokens = maxTokens;
        }
    }

    // ==================== 提问埋点 ====================

    /** 记录用户提问；插入时 answered=0，回答成功后由 markQuestionAnswered 置 1 */
    private Long recordQuestionLog(Long userId, AssistantChatRequest request, String questionText, Long sessionId) {
        try {
            QuestionLog questionLog = new QuestionLog();
            questionLog.setUserId(userId);
            questionLog.setSessionId(sessionId);
            questionLog.setPageRoute(request.getPageContext() != null ? request.getPageContext().getRouteName() : null);
            questionLog.setQuestionText(questionText);
            questionLog.setQuestionHash(md5(questionText));
            questionLog.setAnswered(0);
            questionLogMapper.insert(questionLog);
            return questionLog.getId();
        } catch (Exception e) {
            // 埋点失败不影响主流程
            return null;
        }
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

    private static String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(input.hashCode());
        }
    }
}
