package com.demand.system.module.assistant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.demand.system.common.exception.BusinessException;
import com.demand.system.common.result.ErrorCode;
import com.demand.system.module.assistant.dto.AssistantAction;
import com.demand.system.module.assistant.dto.AssistantSource;
import com.demand.system.module.assistant.dto.AssistantChatRequest;
import com.demand.system.module.assistant.dto.AssistantMessageVO;
import com.demand.system.module.assistant.dto.AssistantOperationAdvice;
import com.demand.system.module.assistant.dto.AssistantSessionCreateDTO;
import com.demand.system.module.assistant.dto.AssistantSessionVO;
import com.demand.system.module.assistant.entity.AssistantMessage;
import com.demand.system.module.assistant.entity.AssistantSession;
import com.demand.system.module.assistant.mapper.AssistantMessageMapper;
import com.demand.system.module.assistant.mapper.AssistantSessionMapper;
import com.demand.system.module.assistant.service.AssistantOperationCatalogService;
import com.demand.system.module.assistant.service.AssistantService;
import com.demand.system.module.assistant.validator.AssistantActionValidator;
import com.demand.system.module.auth.security.SecurityUtils;
import com.demand.system.module.knowledge.dto.KnowledgeSearchRequest;
import com.demand.system.module.knowledge.dto.KnowledgeSearchResponse;
import com.demand.system.module.knowledge.service.KnowledgeSearchService;
import com.demand.system.module.knowledge.llm.LlmGateway;
import com.demand.system.module.knowledge.llm.LlmGatewayConfig;
import com.demand.system.module.llm.constant.LlmApplicationCode;
import com.demand.system.module.llm.service.LlmModelResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

@Service
public class AssistantServiceImpl implements AssistantService {
    private static final Logger log = LoggerFactory.getLogger(AssistantServiceImpl.class);
    private static final long STREAM_TIMEOUT_MS = 120_000L;
    private static final String DEFAULT_SESSION_TITLE = "新会话";

    private final AssistantSessionMapper sessionMapper;
    private final AssistantMessageMapper messageMapper;
    private final AssistantOperationCatalogService catalogService;
    private final AssistantActionValidator actionValidator;
    private final LlmGateway llmGateway;
    private final LlmModelResolver llmModelResolver;
    private final KnowledgeSearchService knowledgeSearchService;

    public AssistantServiceImpl(AssistantSessionMapper sessionMapper,
                                AssistantMessageMapper messageMapper,
                                AssistantOperationCatalogService catalogService,
                                AssistantActionValidator actionValidator,
                                LlmGateway llmGateway,
                                LlmModelResolver llmModelResolver,
                                KnowledgeSearchService knowledgeSearchService) {
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.catalogService = catalogService;
        this.actionValidator = actionValidator;
        this.llmGateway = llmGateway;
        this.llmModelResolver = llmModelResolver;
        this.knowledgeSearchService = knowledgeSearchService;
    }

    @Override
    public List<AssistantSessionVO> listSessions() {
        Long userId = requireCurrentUserId();
        List<AssistantSession> sessions = sessionMapper.selectList(
                new LambdaQueryWrapper<AssistantSession>()
                        .eq(AssistantSession::getUserId, userId)
                        .orderByDesc(AssistantSession::getUpdatedAt)
        );
        List<AssistantSessionVO> result = new ArrayList<>();
        for (AssistantSession session : sessions) {
            AssistantSessionVO vo = toSessionVO(session);
            AssistantMessage latestMessage = findLatestMessage(session.getId());
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

        AssistantMessage assistantMessage = new AssistantMessage();
        assistantMessage.setSessionId(sessionId);
        assistantMessage.setUserId(userId);
        assistantMessage.setRole("assistant");
        assistantMessage.setContent("");
        assistantMessage.setStatus("streaming");
        assistantMessage.setPageContext(request.getPageContext());
        messageMapper.insert(assistantMessage);

        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        if (request.getKnowledgeBaseId() != null) {
            // 知识库检索问答分支：跨全部或指定知识库做 RAG 问答
            assistantMessage.setIntent("knowledge_qa");
            messageMapper.updateById(assistantMessage);
            updateSessionAfterInteraction(session, buildPreview(userContent));
            CompletableFuture.runAsync(() -> doStreamKnowledgeReply(emitter, request, userContent, userMessage.getId(), assistantMessage));
        } else {
            // 通用操作导航分支（原有逻辑）
            AssistantOperationAdvice advice = catalogService.advise(userContent, request.getPageContext(), permissions, superAdmin);
            List<AssistantAction> validatedActions = actionValidator.sanitize(advice.getActions(), permissions, superAdmin);
            advice.setActions(validatedActions);
            assistantMessage.setIntent(advice.getIntent());
            assistantMessage.setActions(validatedActions);
            assistantMessage.setSources(advice.getSources());
            messageMapper.updateById(assistantMessage);
            updateSessionAfterInteraction(session, advice.getSessionTitle());
            CompletableFuture.runAsync(() -> doStreamReply(emitter, advice, userContent, request.getPageContext(), userMessage.getId(), assistantMessage, request.getLlmModelId()));
        }
        return emitter;
    }

    private void doStreamReply(SseEmitter emitter,
                               AssistantOperationAdvice advice,
                               String userMessage,
                               com.demand.system.module.assistant.dto.AssistantPageContext pageContext,
                               Long userMessageId,
                               AssistantMessage assistantMessage,
                               Long llmModelId) {
        StringBuilder answer = new StringBuilder();
        try {
            emitter.send(SseEmitter.event().name("meta").data(Map.of(
                    "sessionId", assistantMessage.getSessionId(),
                    "userMessageId", userMessageId,
                    "assistantMessageId", assistantMessage.getId()
            )));
            emitter.send(SseEmitter.event().name("actions").data(Map.of(
                    "intent", advice.getIntent(),
                    "actions", advice.getActions(),
                    "sources", advice.getSources()
            )));

            ResolvedChatModel chatModel = resolveChatModel(llmModelId);
            if (chatModel != null) {
                String systemPrompt = buildSystemPrompt(pageContext, advice);
                String finalFallbackAnswer = advice.getFallbackAnswer();
                try {
                    llmGateway.streamChatWithProvider(
                            chatModel.provider,
                            systemPrompt,
                            buildUserPrompt(userMessage, advice, finalFallbackAnswer),
                            chatModel.temperature,
                            chatModel.maxTokens,
                            token -> pushDelta(emitter, answer, token)
                    );
                } catch (Exception llmError) {
                    log.warn("assistant llm stream failed, fallback to rule-based answer, sessionId={}", assistantMessage.getSessionId(), llmError);
                }
            }

            if (answer.length() == 0) {
                streamFallbackText(emitter, answer, advice.getFallbackAnswer());
            }

            assistantMessage.setContent(answer.toString());
            assistantMessage.setStatus("completed");
            assistantMessage.setActions(advice.getActions());
            assistantMessage.setSources(advice.getSources());
            assistantMessage.setIntent(advice.getIntent());
            messageMapper.updateById(assistantMessage);

            try {
                emitter.send(SseEmitter.event().name("done").data(toMessageVO(assistantMessage)));
            } catch (Exception doneSendError) {
                log.info("assistant stream done event send skipped, sessionId={}", assistantMessage.getSessionId(), doneSendError);
            }
            emitter.complete();
        } catch (Exception e) {
            log.warn("assistant stream failed, sessionId={}", assistantMessage.getSessionId(), e);
            if (answer.length() == 0) {
                answer.append(advice.getFallbackAnswer());
            }
            assistantMessage.setContent(answer.toString());
            assistantMessage.setStatus("failed");
            assistantMessage.setActions(advice.getActions());
            assistantMessage.setSources(advice.getSources());
            assistantMessage.setIntent(advice.getIntent());
            messageMapper.updateById(assistantMessage);
            try {
                emitter.send(SseEmitter.event().name("error").data(Map.of(
                        "message", e.getMessage() != null ? e.getMessage() : "操作助手响应失败"
                )));
            } catch (Exception ex) {
                log.debug("SSE 发送 error 事件失败(客户端可能已断开)", ex);
            }
            emitter.completeWithError(e);
        }
    }

    /**
     * 知识库检索问答流式回复：检索全部/指定知识库，生成答案并逐字推送，
     * 命中的文档以 sources 形式返回给前端用于展示"依据"。
     */
    private void doStreamKnowledgeReply(SseEmitter emitter,
                                         AssistantChatRequest request,
                                         String userMessage,
                                         Long userMessageId,
                                         AssistantMessage assistantMessage) {
        StringBuilder answer = new StringBuilder();
        try {
            emitter.send(SseEmitter.event().name("meta").data(Map.of(
                    "sessionId", assistantMessage.getSessionId(),
                    "userMessageId", userMessageId,
                    "assistantMessageId", assistantMessage.getId()
            )));

            Long rawKbId = request.getKnowledgeBaseId();
            // -1 表示全部知识库，传 null 给检索服务表示不限定
            Long searchKbId = (rawKbId != null && rawKbId == -1L) ? null : rawKbId;

            KnowledgeSearchRequest searchRequest = new KnowledgeSearchRequest();
            searchRequest.setQuery(userMessage);
            searchRequest.setKnowledgeBaseId(searchKbId);
            searchRequest.setMode(request.getMode() != null ? request.getMode() : "hybrid");
            searchRequest.setTopK(request.getTopK() != null ? request.getTopK() : 10);
            searchRequest.setLlmModelId(request.getLlmModelId());

            KnowledgeSearchResponse searchResponse = knowledgeSearchService.search(searchRequest);

            // 发送思维链步骤（RAG 检索过程）
            if (searchResponse.getThinkingSteps() != null && !searchResponse.getThinkingSteps().isEmpty()) {
                emitter.send(SseEmitter.event().name("thinkingSteps").data(searchResponse.getThinkingSteps()));
            }

            List<AssistantSource> sources = mapCitationsToSources(searchResponse.getCitations(), rawKbId);
            assistantMessage.setSources(sources);
            assistantMessage.setIntent("knowledge_qa");

            // 保存 RAG 检索结果到消息实体
            assistantMessage.setThinkingSteps(searchResponse.getThinkingSteps());
            assistantMessage.setProcessSummary(searchResponse.getProcessSummary());
            // retrievedCount 从 results 或 citations 计算
            Integer retrievedCount = (searchResponse.getResults() != null)
                ? searchResponse.getResults().size()
                : (searchResponse.getCitations() != null ? searchResponse.getCitations().size() : 0);
            assistantMessage.setRetrievedCount(retrievedCount);
            assistantMessage.setCitations(searchResponse.getCitations());

            // 先下发 actions（携带命中文档 sources），让前端展示"依据"
            emitter.send(SseEmitter.event().name("actions").data(Map.of(
                    "intent", "knowledge_qa",
                    "actions", List.of(),
                    "sources", sources
            )));

            String answerText = (searchResponse.getAnswer() != null && !searchResponse.getAnswer().isBlank())
                    ? searchResponse.getAnswer()
                    : "未在所选知识库中找到相关内容，建议换个问法，或切换到其它知识库范围后再试。";
            streamFallbackText(emitter, answer, answerText);

            assistantMessage.setContent(answer.toString());
            assistantMessage.setStatus("completed");
            messageMapper.updateById(assistantMessage);

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
            messageMapper.updateById(assistantMessage);
            try {
                emitter.send(SseEmitter.event().name("error").data(Map.of("message", fallback)));
            } catch (Exception ex) {
                log.debug("SSE 发送 error 事件失败(客户端可能已断开)", ex);
            }
            emitter.completeWithError(e);
        }
    }

    private List<AssistantSource> mapCitationsToSources(List<KnowledgeSearchResponse.CitationReference> citations, Long rawKbId) {
        if (citations == null || citations.isEmpty()) {
            return List.of();
        }
        List<AssistantSource> sources = new ArrayList<>();
        for (KnowledgeSearchResponse.CitationReference citation : citations) {
            AssistantSource source = new AssistantSource();
            source.setCode("knowledge_document");
            source.setTitle(citation.getFileName());
            if (rawKbId != null && rawKbId != -1L) {
                source.setPath("/settings/knowledge/" + rawKbId);
            } else {
                source.setPath("/settings/knowledge");
            }
            int hitCount = citation.getHitCount() != null ? citation.getHitCount() : 0;
            double maxScore = citation.getMaxScore() != null ? citation.getMaxScore() : 0d;
            source.setReason("命中 " + hitCount + " 个片段，相关度 " + Math.round(maxScore * 100) + "%");
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
        vo.setProcessSummary(message.getProcessSummary());
        vo.setRetrievedCount(message.getRetrievedCount());
        vo.setCitations(message.getCitations());
        vo.setCreatedAt(message.getCreatedAt());
        return vo;
    }

    private AssistantMessage findLatestMessage(Long sessionId) {
        return messageMapper.selectOne(
                new LambdaQueryWrapper<AssistantMessage>()
                        .eq(AssistantMessage::getSessionId, sessionId)
                        .orderByDesc(AssistantMessage::getId)
                        .last("LIMIT 1")
        );
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
}
