package com.demand.system.module.knowledge.service;

import java.util.List;

public interface EmbeddingService {

    List<float[]> embed(List<String> texts);

    float[] embed(String text);

    List<Double> rerank(String query, List<String> documents);

    /**
     * 获取当前默认 embedding 模型的配置（chunkSize、chunkOverlap、searchTopK）。
     * 如果模型未配置，返回 null。
     */
    EmbeddingModelConfig getDefaultModelConfig();

    /**
     * Embedding 模型配置（支持模型级别覆盖）
     */
    record EmbeddingModelConfig(int chunkSize, int chunkOverlap, int searchTopK) {}
}
