package com.demand.system.module.knowledge.service.impl;

import com.demand.system.common.exception.BusinessException;
import com.demand.system.common.result.ErrorCode;
import com.demand.system.module.knowledge.dto.KnowledgeSearchResponse;
import com.demand.system.module.knowledge.llm.LlmGateway;
import com.demand.system.module.knowledge.llm.LlmGatewayConfig;
import com.demand.system.module.knowledge.service.RagAnswerService;
import com.demand.system.module.llm.constant.LlmApplicationCode;
import com.demand.system.module.llm.service.LlmModelResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
public class RagAnswerServiceImpl implements RagAnswerService {
    private static final Logger log = LoggerFactory.getLogger(RagAnswerServiceImpl.class);
    private static final String SYSTEM_PROMPT = """
            你是一个专业的知识库问答助手。根据提供的参考资料回答用户问题。

            【引用格式】
            - 在回答中，每当引用某段参考资料时，在该位置插入角标 [N]，N 从 1 开始依次编号，与下方引用列表一一对应。
            - 角标格式：在方括号内直接写数字，如 [1]、[2]、[3]。
            - 如果同一段参考资料被多次引用，重复使用同一个角标。

            【回答要求】
            1. 只根据提供的参考资料回答，不要编造信息
            2. 直接总结和整合信息，不要逐条罗列片段原文
            3. 回答结构清晰：先给总结性结论，再按逻辑分层展开
            4. 回答末尾列出引用来源清单（按角标顺序），格式： [N] 文档名称
            5. 如果参考资料中没有相关信息，明确告知用户
            6. 回答简洁、准确、有条理

            【示例格式】
            根据知识库中的文档，审批流程分为三个主要阶段[1][2]。

            1. 提交阶段：需求提出后，由项目负责人进行初审[1]。
            2. 评审阶段：初审通过后，组织相关方进行技术评审[2]。
            3. 审批阶段：评审通过后，由部门主管最终审批[1][2]。

            引用来源：
            [1] 需求管理规范V2.0
            [2] 项目审批流程指南
            """;

    private final LlmGateway llmGateway;
    private final LlmModelResolver llmModelResolver;

    public RagAnswerServiceImpl(LlmGateway llmGateway,
                               LlmModelResolver llmModelResolver) {
        this.llmGateway = llmGateway;
        this.llmModelResolver = llmModelResolver;
    }

    @Override
    public String generateAnswer(
            String query,
            List<KnowledgeSearchResponse.SearchResultItem> searchResults,
            Long knowledgeBaseId,
            Long llmModelId
    ) {
        LlmGateway.ChatResult result = generateAnswerWithReasoning(query, searchResults, knowledgeBaseId, llmModelId);
        return result != null ? result.getContent() : null;
    }

    @Override
    public LlmGateway.ChatResult generateAnswerWithReasoning(
            String query,
            List<KnowledgeSearchResponse.SearchResultItem> searchResults,
            Long knowledgeBaseId,
            Long llmModelId
    ) {
        String context = buildContext(searchResults);
        String userMessage = "问题：" + query + "\n\n参考资料：\n" + context;

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
    public void streamAnswer(
            String query,
            List<KnowledgeSearchResponse.SearchResultItem> searchResults,
            Long knowledgeBaseId,
            Long llmModelId,
            Consumer<String> tokenConsumer
    ) {
        String context = buildContext(searchResults);
        String userMessage = "问题：" + query + "\n\n参考资料：\n" + context;

        invokeChatWithFallback(llmModelId, (resolution) -> {
            llmGateway.streamChatWithProvider(
                    resolution.provider(),
                    SYSTEM_PROMPT,
                    userMessage,
                    resolution.temperature(),
                    resolution.maxTokens(),
                    tokenConsumer
            );
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
            // 用户显式选择了模型 → 只用该模型，不兜底
            if (llmModelId != null) {
                ChatProviderResolution resolution = buildProviderFromModel(llmModelId);
                return caller.apply(resolution);
            }

            // 未指定模型 → 解析默认+兜底列表
            List<ChatProviderResolution> resolutions = resolveChatProviders();

            // 逐一尝试，第一个成功即返回
            for (ChatProviderResolution resolution : resolutions) {
                try {
                    T result = caller.apply(resolution);
                    log.info("Chat 调用成功: model={}", resolution.provider().getModel());
                    return result;
                } catch (Exception e) {
                    log.warn("Chat 调用失败: model={}, error={}", resolution.provider().getModel(), e.getMessage());
                    // 继续尝试下一个兜底模型
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
        log.info("Chat: 应用[{}]可用模型列表(应用指定优先): {}",
                LlmApplicationCode.KNOWLEDGE_ANSWER,
                resolutions.stream().map(r -> r.provider().getModel()).toList());
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

    private String buildContext(List<KnowledgeSearchResponse.SearchResultItem> results) {
        return results.stream()
                .map(item -> {
                    StringBuilder sb = new StringBuilder();
                    sb.append("【来源：").append(item.getFileName() != null ? item.getFileName() : "未知文档").append("】\n");
                    if (item.getSectionTitle() != null) {
                        sb.append("章节：").append(item.getSectionTitle()).append("\n");
                    }
                    sb.append("内容：").append(item.getContent());
                    return sb.toString();
                })
                .collect(Collectors.joining("\n\n---\n\n"));
    }
}
