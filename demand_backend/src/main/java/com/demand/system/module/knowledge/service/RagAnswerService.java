package com.demand.system.module.knowledge.service;

import com.demand.system.module.knowledge.dto.KnowledgeSearchResponse;

import java.util.List;

public interface RagAnswerService {

    String generateAnswer(String query, List<KnowledgeSearchResponse.SearchResultItem> searchResults, Long knowledgeBaseId);
}
