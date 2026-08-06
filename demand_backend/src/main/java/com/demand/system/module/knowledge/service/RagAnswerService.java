package com.demand.system.module.knowledge.service;

import com.demand.system.module.knowledge.dto.KnowledgeSearchResponse;
import com.demand.system.module.knowledge.llm.LlmGateway;

import java.util.List;
import java.util.function.Consumer;

public interface RagAnswerService {

    String generateAnswer(
            String query,
            List<KnowledgeSearchResponse.SearchResultItem> searchResults,
            Long knowledgeBaseId,
            Long llmModelId
    );

    /**
     * 生成答案并返回完整结果（含深度思考 reasoning 内容与 token 用量）。
     */
    LlmGateway.ChatResult generateAnswerWithReasoning(
            String query,
            List<KnowledgeSearchResponse.SearchResultItem> searchResults,
            Long knowledgeBaseId,
            Long llmModelId
    );

    void streamAnswer(
            String query,
            List<KnowledgeSearchResponse.SearchResultItem> searchResults,
            Long knowledgeBaseId,
            Long llmModelId,
            Consumer<String> tokenConsumer
    );
}
