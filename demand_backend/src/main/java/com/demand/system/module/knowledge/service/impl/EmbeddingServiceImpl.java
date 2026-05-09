package com.demand.system.module.knowledge.service.impl;

import com.demand.system.module.knowledge.llm.LlmGateway;
import com.demand.system.module.knowledge.service.EmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingServiceImpl implements EmbeddingService {

    private final LlmGateway llmGateway;

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
