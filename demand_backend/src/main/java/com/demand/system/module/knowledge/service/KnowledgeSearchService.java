package com.demand.system.module.knowledge.service;

import com.demand.system.module.knowledge.dto.KnowledgeSearchRequest;
import com.demand.system.module.knowledge.dto.KnowledgeSearchResponse;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface KnowledgeSearchService {

    KnowledgeSearchResponse search(KnowledgeSearchRequest request);

    SseEmitter streamSearch(KnowledgeSearchRequest request);
}
