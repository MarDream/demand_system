package com.demand.system.module.knowledge.service.impl;

import com.demand.system.module.knowledge.llm.LlmGateway;
import com.demand.system.module.knowledge.service.EmbeddingService;
import org.springframework.stereotype.Service;

import java.util.List;


@Service
public class EmbeddingServiceImpl implements EmbeddingService {
    private final LlmGateway llmGateway;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(EmbeddingServiceImpl.class);

    public EmbeddingServiceImpl(LlmGateway llmGateway) {
        this.llmGateway = llmGateway;
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        return llmGateway.embed(texts);
    }

    @Override
    public float[] embed(String text) {
        return llmGateway.embed(text);
    }

    @Override
    public List<Double> rerank(String query, List<String> documents) {
        return llmGateway.rerank(query, documents);
    }
}
