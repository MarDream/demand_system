package com.demand.system.module.assistant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.demand.system.common.exception.BusinessException;
import com.demand.system.common.result.ErrorCode;
import com.demand.system.module.assistant.dto.AssistantAction;
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

    public AssistantServiceImpl(AssistantSessionMapper sessionMapper,
                                AssistantMessageMapper messageMapper,
                                AssistantOperationCatalogService catalogService,
                                AssistantActionValidator actionValidator,
                                LlmGateway llmGateway,
                                LlmModelResolver llmModelResolver) {
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.catalogService = catalogService;
        this.actionValidator = actionValidator;
        this.llmGateway = llmGateway;
        this.llmModelResolver = llmModelResolver;
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

        AssistantOperationAdvice advice = catalogService.advise(userContent, request.getPageContext(), permissions, superAdmin);
        List<AssistantAction> validatedActions = actionValidator.sanitize(advice.getActions(), permissions, superAdmin);
        advice.setActions(validatedActions);

        AssistantMessage assistantMessage = new AssistantMessage();
        assistantMessage.setSessionId(sessionId);
        assistantMessage.setUserId(userId);
        assistantMessage.setRole("assistant");
        assistantMessage.setContent("");
        assistantMessage.setStatus("streaming");
        assistantMessage.setIntent(advice.getIntent());
        assistantMessage.setActions(validatedActions);
        assistantMessage.setSources(advice.getSources());
        assistantMessage.setPageContext(request.getPageContext());
        messageMapper.insert(assistantMessage);

        updateSessionAfterInteraction(session, advice.getSessionTitle());

        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        CompletableFuture.runAsync(() -> doStreamReply(emitter, advice, userContent, request.getPageContext(), userMessage.getId(), assistantMessage));
        return emitter;
    }

    private void doStreamReply(SseEmitter emitter,
                               AssistantOperationAdvice advice,
                               String userMessage,
                               com.demand.system.module.assistant.dto.AssistantPageContext pageContext,
                               Long userMessageId,
                               AssistantMessage assistantMessage) {
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

            ResolvedChatModel chatModel = resolveChatModel();
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
            } catch (Exception ignored) {
                // ignore client disconnect errors
            }
            emitter.completeWithError(e);
        }
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

    private ResolvedChatModel resolveChatModel() {
        try {
            LlmModelResolver.ResolvedModel resolved = llmModelResolver.resolveFirst(LlmApplicationCode.ASSISTANT_CHAT);
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
