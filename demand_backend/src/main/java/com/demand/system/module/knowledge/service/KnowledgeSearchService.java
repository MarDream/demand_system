package com.demand.system.module.knowledge.service;

import com.demand.system.module.knowledge.dto.KnowledgeSearchRequest;
import com.demand.system.module.knowledge.dto.KnowledgeSearchResponse;

public interface KnowledgeSearchService {

    KnowledgeSearchResponse search(KnowledgeSearchRequest request);
}
