package com.demand.system.module.knowledge.service;

import java.util.List;

public interface EmbeddingService {

    List<float[]> embed(List<String> texts);

    float[] embed(String text);

    List<Double> rerank(String query, List<String> documents);
}
