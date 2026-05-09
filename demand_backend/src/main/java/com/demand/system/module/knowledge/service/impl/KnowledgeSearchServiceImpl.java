package com.demand.system.module.knowledge.service.impl;

import com.demand.system.module.knowledge.config.KnowledgeConfig;
import com.demand.system.module.knowledge.dto.KnowledgeSearchRequest;
import com.demand.system.module.knowledge.dto.KnowledgeSearchResponse;
import com.demand.system.module.knowledge.service.EmbeddingService;
import com.demand.system.module.knowledge.service.KnowledgeSearchService;
import com.demand.system.module.knowledge.service.RagAnswerService;
import com.demand.system.module.knowledge.vectorstore.MilvusVectorStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeSearchServiceImpl implements KnowledgeSearchService {

    private final EmbeddingService embeddingService;
    private final MilvusVectorStore milvusVectorStore;
    private final KnowledgeConfig knowledgeConfig;
    private final RagAnswerService ragAnswerService;

    @Override
    public KnowledgeSearchResponse search(KnowledgeSearchRequest request) {
        String mode = request.getMode() != null ? request.getMode() : "hybrid";
        int topK = request.getTopK() != null ? request.getTopK() : knowledgeConfig.getSearchTopK();
        String kbId = request.getKnowledgeBaseId() != null ? String.valueOf(request.getKnowledgeBaseId()) : null;

        float[] queryVector = embeddingService.embed(request.getQuery());

        List<KnowledgeSearchResponse.SearchResultItem> results;

        if ("semantic".equals(mode)) {
            results = semanticSearch(queryVector, kbId, topK);
        } else if ("keyword".equals(mode)) {
            results = semanticSearch(queryVector, kbId, topK);
        } else {
            results = hybridSearch(request.getQuery(), queryVector, kbId, topK);
        }

        KnowledgeSearchResponse.KnowledgeSearchResponseBuilder responseBuilder = KnowledgeSearchResponse.builder()
                .results(results)
                .total(results.size());

        // RAG模式：生成LLM答案
        if ("rag".equals(mode) && !results.isEmpty()) {
            try {
                String answer = ragAnswerService.generateAnswer(request.getQuery(), results, request.getKnowledgeBaseId());
                responseBuilder.answer(answer);
            } catch (Exception e) {
                log.warn("RAG答案生成失败，仅返回检索结果", e);
                responseBuilder.answer(null);
            }
        }

        return responseBuilder.build();
    }

    private List<KnowledgeSearchResponse.SearchResultItem> semanticSearch(
            float[] queryVector, String knowledgeBaseId, int topK) {
        List<MilvusVectorStore.SearchResult> milvusResults =
                milvusVectorStore.search(queryVector, knowledgeBaseId, topK);

        return milvusResults.stream()
                .map(this::toResultItem)
                .collect(Collectors.toList());
    }

    private List<KnowledgeSearchResponse.SearchResultItem> hybridSearch(
            String query, float[] queryVector, String knowledgeBaseId, int topK) {
        int candidateSize = Math.min(topK * 5, 100);
        List<MilvusVectorStore.SearchResult> candidates =
                milvusVectorStore.search(queryVector, knowledgeBaseId, candidateSize);

        if (candidates.isEmpty()) {
            return Collections.emptyList();
        }

        try {
            List<String> documents = candidates.stream()
                    .map(r -> String.valueOf(r.getEntity().getOrDefault("text", "")))
                    .collect(Collectors.toList());

            List<Double> rerankScores = embeddingService.rerank(query, documents);

            List<ScoredCandidate> scored = new ArrayList<>();
            for (int i = 0; i < candidates.size(); i++) {
                double rerankScore = i < rerankScores.size() ? rerankScores.get(i) : candidates.get(i).getScore();
                scored.add(new ScoredCandidate(candidates.get(i), rerankScore));
            }

            scored.sort(Comparator.comparingDouble(s -> -s.score));

            return scored.stream()
                    .limit(topK)
                    .map(s -> {
                        KnowledgeSearchResponse.SearchResultItem item = toResultItem(s.result);
                        item.setScore(s.score);
                        return item;
                    })
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.warn("Reranker调用失败，降级使用向量检索结果", e);
            return candidates.stream()
                    .limit(topK)
                    .map(this::toResultItem)
                    .collect(Collectors.toList());
        }
    }

    private KnowledgeSearchResponse.SearchResultItem toResultItem(MilvusVectorStore.SearchResult sr) {
        Map<String, Object> entity = sr.getEntity();
        return KnowledgeSearchResponse.SearchResultItem.builder()
                .chunkId(parseLong(entity.get("id")))
                .documentId(parseLong(entity.get("document_id")))
                .fileName(getString(entity.get("file_name")))
                .sectionTitle(getString(entity.get("section_title")))
                .content(getString(entity.get("text")))
                .pageNum(parseInteger(entity.get("page_num")))
                .score((double) sr.getScore())
                .knowledgeBaseId(getString(entity.get("knowledge_base_id")))
                .build();
    }

    private Long parseLong(Object val) {
        if (val instanceof Number) return ((Number) val).longValue();
        if (val instanceof String) {
            try { return Long.parseLong((String) val); } catch (Exception e) { return null; }
        }
        return null;
    }

    private Integer parseInteger(Object val) {
        if (val instanceof Number) return ((Number) val).intValue();
        if (val instanceof String) {
            try { return Integer.parseInt((String) val); } catch (Exception e) { return null; }
        }
        return null;
    }

    private String getString(Object val) {
        return val != null ? val.toString() : null;
    }

    private record ScoredCandidate(MilvusVectorStore.SearchResult result, double score) {}
}
