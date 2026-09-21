package com.demand.system.module.knowledge.service.impl;

import com.demand.system.common.exception.BusinessException;
import com.demand.system.common.result.ErrorCode;
import com.demand.system.module.knowledge.dto.ConversationTurn;
import com.demand.system.module.knowledge.dto.KnowledgeSearchResponse;
import com.demand.system.module.knowledge.llm.LlmGateway;
import com.demand.system.module.knowledge.llm.LlmGateway.ChatUsage;
import com.demand.system.module.knowledge.llm.LlmGatewayConfig;
import com.demand.system.module.knowledge.service.RagAnswerService;
import com.demand.system.module.llm.constant.LlmApplicationCode;
import com.demand.system.module.llm.service.LlmModelResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

@Service
public class RagAnswerServiceImpl implements RagAnswerService {
    private static final Logger log = LoggerFactory.getLogger(RagAnswerServiceImpl.class);
    private static final String SYSTEM_PROMPT = """
            你是一个专业的知识库问答助手。根据提供的参考资料回答用户问题。

            【引用格式】
            - 每段参考资料都带有唯一编号（如 资料[1]、资料[2]，同一文档的多段资料共用同一编号）。
            - 回答中每当引用某段资料时，在对应位置原样插入其编号角标，如 [1]、[2]、[1][3]。
            - 角标必须使用资料自带的编号，禁止自行重新编号或使用不存在的编号。
            - 同一资料被多次引用时，重复使用同一角标。
            - 角标直接写在被支撑的结论之后，不要写成「（资料[1]）」「见资料1」这类带前缀或说明的写法。
            - 标注「无编号，不可引用」的参考资料只能作为背景理解，不要为其插入任何角标。
            - 只引用真正支撑该结论的资料；没有资料支撑的句子不要挂角标。

            【回答要求】
            1. 只根据提供的参考资料回答，不要编造信息
            2. 直接总结和整合信息，不要逐条罗列片段原文
            3. 回答结构清晰：先给总结性结论，再按逻辑分层展开
            4. 不要在回答末尾手写"引用来源"清单，来源列表由系统自动展示
            5. 如果参考资料中没有相关信息，明确告知用户
            6. 回答简洁、准确、有条理

            【示例格式】
            （假设资料[1]是需求管理规范，资料[2]是审批流程指南）

            根据知识库中的文档，审批流程分为三个主要阶段[1][2]。

            1. 提交阶段：需求提出后，由项目负责人进行初审[1]。
            2. 评审阶段：初审通过后，组织相关方进行技术评审[2]。
            3. 审批阶段：评审通过后，由部门主管最终审批[1][2]。
            """;

    /** 多轮场景注入提示词的最大历史轮数（单轮按 user+assistant 各一条计） */
    private static final int MAX_HISTORY_MESSAGES = 6;
    private static final int MAX_HISTORY_TURN_CHARS = 500;

    private final LlmGateway llmGateway;
    private final LlmModelResolver llmModelResolver;

    public RagAnswerServiceImpl(LlmGateway llmGateway,
                               LlmModelResolver llmModelResolver) {
        this.llmGateway = llmGateway;
        this.llmModelResolver = llmModelResolver;
    }

    @Override
    public LlmGateway.ChatResult generateAnswerWithReasoning(
            String query,
            List<KnowledgeSearchResponse.SearchResultItem> searchResults,
            Long knowledgeBaseId,
            Long llmModelId,
            List<ConversationTurn> history
    ) {
        String userMessage = buildUserMessage(query, searchResults, history);

        return invokeChatWithFallback(llmModelId, (resolution) -> {
            Map<String, Object> thinkingParams = llmGateway.buildThinkingParams(resolution.provider(), resolution.maxTokens());
            try {
                return llmGateway.chatWithProviderWithThinking(
                        resolution.provider(),
                        SYSTEM_PROMPT,
                        userMessage,
                        resolution.temperature(),
                        resolution.maxTokens(),
                        thinkingParams
                );
            } catch (Exception e) {
                log.warn("RAG 深度思考模式调用失败，降级为普通模式: {}", e.getMessage());
                return llmGateway.chatWithProvider(
                        resolution.provider(),
                        SYSTEM_PROMPT,
                        userMessage,
                        resolution.temperature(),
                        resolution.maxTokens()
                );
            }
        });
    }

    @Override
    public void streamAnswerWithReasoning(
            String query,
            List<KnowledgeSearchResponse.SearchResultItem> searchResults,
            Long knowledgeBaseId,
            Long llmModelId,
            List<ConversationTurn> history,
            Consumer<String> tokenConsumer,
            Consumer<String> reasoningConsumer,
            Consumer<ChatUsage> usageConsumer
    ) {
        String userMessage = buildUserMessage(query, searchResults, history);

        invokeChatWithFallback(llmModelId, (resolution) -> {
            AtomicBoolean anyOutput = new AtomicBoolean(false);
            Consumer<String> guardedToken = countOutput(anyOutput, tokenConsumer);
            Consumer<String> guardedReasoning = countOutput(anyOutput, reasoningConsumer);

            Map<String, Object> thinkingParams = llmGateway.buildThinkingParams(resolution.provider(), resolution.maxTokens());
            try {
                llmGateway.streamChatWithProvider(
                        resolution.provider(),
                        SYSTEM_PROMPT,
                        userMessage,
                        resolution.temperature(),
                        resolution.maxTokens(),
                        thinkingParams,
                        guardedToken,
                        guardedReasoning,
                        usageConsumer
                );
            } catch (Exception e) {
                // 仅当深度思考参数导致一开始就失败时才降级重试；已有输出则保留部分结果，避免重复推送
                if (anyOutput.get()) {
                    throw e;
                }
                log.warn("RAG 流式深度思考模式调用失败，降级为普通模式重试: {}", e.getMessage());
                llmGateway.streamChatWithProvider(
                        resolution.provider(),
                        SYSTEM_PROMPT,
                        userMessage,
                        resolution.temperature(),
                        resolution.maxTokens(),
                        null,
                        guardedToken,
                        guardedReasoning,
                        usageConsumer
                );
            }
            return null;
        });
    }

    // ==================== Chat 模型解析（默认 + 兜底列表） ====================

    /**
     * Chat 模型调用入口，支持默认+兜底：
     * 1. 前端指定了 llmModelId → 直接使用该模型，失败不兜底（用户显式选择）
     * 2. 未指定 → 查出所有 enabled Chat 模型（默认排前），逐一尝试，第一个成功即返回
     * 3. 全部失败 → 抛出友好异常
     */
    private <T> T invokeChatWithFallback(Long llmModelId, java.util.function.Function<ChatProviderResolution, T> caller) {
        try {
            if (llmModelId != null) {
                ChatProviderResolution resolution = buildProviderFromModel(llmModelId);
                return caller.apply(resolution);
            }

            List<ChatProviderResolution> resolutions = resolveChatProviders();
            for (ChatProviderResolution resolution : resolutions) {
                try {
                    T result = caller.apply(resolution);
                    log.info("Chat 调用成功: model={}", resolution.provider().getModel());
                    return result;
                } catch (Exception e) {
                    log.warn("Chat 调用失败: model={}, error={}", resolution.provider().getModel(), e.getMessage());
                }
            }

            throw new BusinessException(ErrorCode.BAD_REQUEST, "所有 Chat 模型均调用失败，请检查模型配置。");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("RAG答案生成失败", e);
            throw new RuntimeException("答案生成失败: " + e.getMessage());
        }
    }

    /**
     * 解析所有可用的 Chat 模型（默认在前，兜底在后）。
     * 排除 embedding 和 rerank 类型，只取对话模型。
     */
    private List<ChatProviderResolution> resolveChatProviders() {
        List<LlmModelResolver.ResolvedModel> resolvedModels =
                llmModelResolver.resolveCandidates(LlmApplicationCode.KNOWLEDGE_ANSWER);
        if (resolvedModels.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请先配置可用的 Chat 对话模型。");
        }

        List<ChatProviderResolution> resolutions = new ArrayList<>();
        for (LlmModelResolver.ResolvedModel resolved : resolvedModels) {
            LlmGatewayConfig.Provider provider = llmModelResolver.toGatewayProvider(resolved);
            resolutions.add(new ChatProviderResolution(
                    provider,
                    resolved.model().getTemperature(),
                    resolved.model().getMaxTokens()
            ));
        }
        return resolutions;
    }

    private ChatProviderResolution buildProviderFromModel(Long modelId) {
        LlmModelResolver.ResolvedModel resolved =
                llmModelResolver.resolveModel(modelId, LlmApplicationCode.KNOWLEDGE_ANSWER);
        if (resolved == null) {
            throw new RuntimeException("所选问答模型不存在、未启用或类型不匹配");
        }

        return new ChatProviderResolution(
                llmModelResolver.toGatewayProvider(resolved),
                resolved.model().getTemperature(),
                resolved.model().getMaxTokens()
        );
    }

    /**
     * Chat 模型解析结果，包含 provider 配置、temperature 和 maxTokens
     */
    private record ChatProviderResolution(
            LlmGatewayConfig.Provider provider,
            java.math.BigDecimal temperature,
            Integer maxTokens
    ) {}

    private Consumer<String> countOutput(AtomicBoolean anyOutput, Consumer<String> delegate) {
        if (delegate == null) {
            return null;
        }
        return token -> {
            if (token != null && !token.isEmpty()) {
                anyOutput.set(true);
                delegate.accept(token);
            }
        };
    }

    private String buildUserMessage(String query,
                                    List<KnowledgeSearchResponse.SearchResultItem> results,
                                    List<ConversationTurn> history) {
        StringBuilder sb = new StringBuilder();
        if (history != null && !history.isEmpty()) {
            sb.append("【对话背景（旧→新），回答当前问题时请保持连贯】\n");
            history.stream()
                    .limit(MAX_HISTORY_MESSAGES)
                    .forEach(turn -> sb.append("user".equals(turn.role()) ? "用户：" : "助手：")
                            .append(abbreviate(turn.safeContent()))
                            .append('\n'));
            sb.append('\n');
        }
        sb.append("问题：").append(query).append("\n\n参考资料：\n").append(buildContext(results));
        return sb.toString();
    }

    private String abbreviate(String text) {
        String compact = text.replaceAll("\\s+", " ").trim();
        return compact.length() <= MAX_HISTORY_TURN_CHARS ? compact : compact.substring(0, MAX_HISTORY_TURN_CHARS) + "…";
    }

    /**
     * 组装参考资料上下文。
     *
     * <p>同一文档的多段片段共用一个编号（按片段在检索结果中的首次出现顺序编号），
     * 编号与 {@code KnowledgeSearchResponse.citations} 的顺序严格一致，
     * 前端据此把回答中的 [N] 角标映射到可预览的来源文档。</p>
     */
    private String buildContext(List<KnowledgeSearchResponse.SearchResultItem> results) {
        Map<Long, Integer> docNumber = new LinkedHashMap<>();
        StringBuilder sb = new StringBuilder();
        for (KnowledgeSearchResponse.SearchResultItem item : results) {
            // 编号只分配给有 documentId 的片段，口径与 citations 完全一致；
            // documentId 为空的片段（如工单正文）不参与编号，否则会把后续文档的编号整体挤位
            Integer number = item.getDocumentId() == null
                    ? null
                    : docNumber.computeIfAbsent(item.getDocumentId(), k -> docNumber.size() + 1);
            sb.append(number != null ? "【资料[" + number + "]｜来源：" : "【参考资料（无编号，不可引用）｜来源：")
              .append(item.getFileName() != null ? item.getFileName() : "未知文档").append("】\n");
            if (item.getSectionTitle() != null) {
                sb.append("章节：").append(item.getSectionTitle()).append("\n");
            }
            sb.append("内容：").append(item.getContent()).append("\n\n---\n\n");
        }
        return sb.toString().trim();
    }
}
