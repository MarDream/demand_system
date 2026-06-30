package com.demand.system.module.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.demand.system.common.exception.BusinessException;
import com.demand.system.common.result.ErrorCode;
import com.demand.system.module.knowledge.dto.KnowledgeSearchResponse;
import com.demand.system.module.knowledge.llm.LlmGateway;
import com.demand.system.module.knowledge.llm.LlmGatewayConfig;
import com.demand.system.module.knowledge.service.RagAnswerService;
import com.demand.system.module.llm.entity.LlmModel;
import com.demand.system.module.llm.entity.LlmProvider;
import com.demand.system.module.llm.mapper.LlmModelMapper;
import com.demand.system.module.llm.mapper.LlmProviderMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
public class RagAnswerServiceImpl implements RagAnswerService {
    private static final Logger log = LoggerFactory.getLogger(RagAnswerServiceImpl.class);
    private static final String SYSTEM_PROMPT = """
            你是一个专业的知识库问答助手。根据提供的参考资料回答用户问题。

            要求：
            1. 只根据提供的参考资料回答，不要编造信息
            2. 如果参考资料中没有相关信息，明确告知用户
            3. 在回答中标注引用来源（文档名称）
            4. 回答要简洁、准确、有条理
            """;

    private final LlmGateway llmGateway;
    private final LlmModelMapper llmModelMapper;
    private final LlmProviderMapper llmProviderMapper;

    public RagAnswerServiceImpl(LlmGateway llmGateway,
                               LlmModelMapper llmModelMapper,
                               LlmProviderMapper llmProviderMapper) {
        this.llmGateway = llmGateway;
        this.llmModelMapper = llmModelMapper;
        this.llmProviderMapper = llmProviderMapper;
    }

    @Override
    public String generateAnswer(
            String query,
            List<KnowledgeSearchResponse.SearchResultItem> searchResults,
            Long knowledgeBaseId,
            Long llmModelId
    ) {
        String context = buildContext(searchResults);
        String userMessage = "问题：" + query + "\n\n参考资料：\n" + context;

        return invokeChatWithFallback(llmModelId, (resolution) ->
                llmGateway.chatWithProvider(
                        resolution.provider(),
                        SYSTEM_PROMPT,
                        userMessage,
                        resolution.temperature(),
                        resolution.maxTokens()
                ).getContent()
        );
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
        List<LlmModel> models = llmModelMapper.selectList(
                new LambdaQueryWrapper<LlmModel>()
                        .eq(LlmModel::getEnabled, true)
                        .notIn(LlmModel::getModelType, "embedding", "rerank")
                        .orderByDesc(LlmModel::getIsDefault)
                        .orderByAsc(LlmModel::getId)
        );

        if (models.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请先配置可用的 Chat 对话模型。");
        }

        List<ChatProviderResolution> resolutions = new ArrayList<>();
        List<String> skippedReasons = new ArrayList<>();

        for (LlmModel model : models) {
            LlmProvider provider = llmProviderMapper.selectById(model.getProviderId());
            if (provider == null) {
                skippedReasons.add(String.format("模型[%s]接入组不存在", model.getModelId()));
                continue;
            }
            if (!Boolean.TRUE.equals(provider.getEnabled())) {
                skippedReasons.add(String.format("模型[%s]接入组[%s]未启用", model.getModelId(), provider.getName()));
                continue;
            }

            LlmGatewayConfig.Provider chatProvider = new LlmGatewayConfig.Provider();
            chatProvider.setProtocol(provider.getProtocol());
            chatProvider.setBaseUrl(provider.getBaseUrl());
            chatProvider.setApiKey(provider.getApiKey());
            chatProvider.setModel(model.getModelId());

            resolutions.add(new ChatProviderResolution(chatProvider, model.getTemperature(), model.getMaxTokens()));
        }

        if (resolutions.isEmpty()) {
            String detail = skippedReasons.isEmpty() ? "" : "（" + String.join("；", skippedReasons) + "）";
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请先配置可用的 Chat 对话模型。" + detail);
        }

        if (!skippedReasons.isEmpty()) {
            log.warn("Chat: 以下模型因配置问题被跳过: {}", String.join("；", skippedReasons));
        }

        log.info("Chat: 可用模型列表(默认优先): {}", resolutions.stream().map(r -> r.provider().getModel()).toList());
        return resolutions;
    }

    private ChatProviderResolution buildProviderFromModel(Long modelId) {
        LlmModel model = llmModelMapper.selectById(modelId);
        if (model == null) {
            throw new RuntimeException("所选问答模型不存在");
        }
        if (!Boolean.TRUE.equals(model.getEnabled())) {
            throw new RuntimeException("所选问答模型未启用");
        }
        return buildProviderFromModelEntity(model);
    }

    private ChatProviderResolution buildProviderFromModelEntity(LlmModel model) {
        LlmProvider provider = llmProviderMapper.selectById(model.getProviderId());
        if (provider == null) {
            throw new RuntimeException("所选模型的接入组不存在");
        }
        if (!Boolean.TRUE.equals(provider.getEnabled())) {
            throw new RuntimeException("所选模型的接入组未启用");
        }

        LlmGatewayConfig.Provider chatProvider = new LlmGatewayConfig.Provider();
        chatProvider.setProtocol(provider.getProtocol());
        chatProvider.setBaseUrl(provider.getBaseUrl());
        chatProvider.setApiKey(provider.getApiKey());
        chatProvider.setModel(model.getModelId());

        return new ChatProviderResolution(chatProvider, model.getTemperature(), model.getMaxTokens());
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
