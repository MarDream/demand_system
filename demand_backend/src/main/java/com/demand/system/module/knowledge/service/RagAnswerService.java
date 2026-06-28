package com.demand.system.module.knowledge.service;

import com.demand.system.module.knowledge.dto.KnowledgeSearchResponse;

import java.util.List;
import java.util.function.Consumer;

public interface RagAnswerService {

    String generateAnswer(
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
