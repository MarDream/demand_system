package com.demand.system.module.knowledge.service.impl;

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

import java.util.List;
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

        try {
            if (llmModelId != null) {
                return chatWithSelectedModel(llmModelId, userMessage);
            }
            return llmGateway.chat(SYSTEM_PROMPT, userMessage);
        } catch (Exception e) {
            log.error("RAG答案生成失败: query={}", query, e);
            throw new RuntimeException("答案生成失败: " + e.getMessage());
        }
    }

    private String chatWithSelectedModel(Long llmModelId, String userMessage) {
        LlmModel model = llmModelMapper.selectById(llmModelId);
        if (model == null) {
            throw new RuntimeException("所选问答模型不存在");
        }
        if (!Boolean.TRUE.equals(model.getEnabled())) {
            throw new RuntimeException("所选问答模型未启用");
        }

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

        return llmGateway.chatWithProvider(
                chatProvider,
                SYSTEM_PROMPT,
                userMessage,
                model.getTemperature(),
                model.getMaxTokens()
        ).getContent();
    }

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
