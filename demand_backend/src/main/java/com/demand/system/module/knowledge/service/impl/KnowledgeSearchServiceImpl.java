package com.demand.system.module.knowledge.service.impl;

import com.demand.system.module.knowledge.entity.KnowledgeDocument;
import com.demand.system.module.knowledge.config.KnowledgeConfig;
import com.demand.system.module.knowledge.dto.KnowledgeSearchRequest;
import com.demand.system.module.knowledge.dto.KnowledgeSearchResponse;
import com.demand.system.module.knowledge.mapper.KnowledgeDocumentMapper;
import com.demand.system.module.requirement.dto.RequirementAttachmentDTO;
import com.demand.system.module.requirement.entity.Requirement;
import com.demand.system.module.requirement.mapper.RequirementMapper;
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
    private final RequirementMapper requirementMapper;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;

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
                .total(results.size())
                .processSummary(buildProcessSummary(request, results.size(), results));

        boolean shouldGenerateAnswer = !results.isEmpty() && ("rag".equals(mode) || request.getLlmModelId() != null);
        if (shouldGenerateAnswer) {
            try {
                String answer = ragAnswerService.generateAnswer(
                        request.getQuery(),
                        results,
                        request.getKnowledgeBaseId(),
                        request.getLlmModelId()
                );
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
        Map<Long, KnowledgeSearchResponse.RequirementReference> reqMap = buildDocumentRequirementMap(milvusResults);
        return milvusResults.stream()
                .map(sr -> toResultItem(sr, reqMap))
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

            Map<Long, KnowledgeSearchResponse.RequirementReference> reqMap = buildDocumentRequirementMap(scored.stream().map(s -> s.result).collect(Collectors.toList()));
            return scored.stream()
                    .limit(topK)
                    .map(s -> {
                        KnowledgeSearchResponse.SearchResultItem item = toResultItem(s.result, reqMap);
                        item.setScore(s.score);
                        return item;
                    })
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.warn("Reranker调用失败，降级使用向量检索结果", e);
            return candidates.stream()
                    .limit(topK)
                    .map(sr -> toResultItem(sr, buildDocumentRequirementMap(candidates.stream().limit(topK).collect(Collectors.toList()))))
                    .collect(Collectors.toList());
        }
    }

    private KnowledgeSearchResponse.SearchResultItem toResultItem(
            MilvusVectorStore.SearchResult sr, Map<Long, KnowledgeSearchResponse.RequirementReference> requirementMap) {
        Map<String, Object> entity = sr.getEntity();
        Long docId = parseLong(entity.get("document_id"));
        return KnowledgeSearchResponse.SearchResultItem.builder()
                .chunkId(parseLong(entity.get("id")))
                .documentId(docId)
                .fileName(getString(entity.get("file_name")))
                .sectionTitle(getString(entity.get("section_title")))
                .content(getString(entity.get("text")))
                .pageNum(parseInteger(entity.get("page_num")))
                .score((double) sr.getScore())
                .knowledgeBaseId(getString(entity.get("knowledge_base_id")))
                .requirement(requirementMap.get(docId))
                .build();
    }

    private Map<Long, KnowledgeSearchResponse.RequirementReference> buildDocumentRequirementMap(
            List<MilvusVectorStore.SearchResult> results) {
        Set<Long> docIds = results.stream()
                .map(sr -> parseLong(sr.getEntity().get("document_id")))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (docIds.isEmpty()) return Collections.emptyMap();

        List<KnowledgeDocument> documents = knowledgeDocumentMapper.selectBatchIds(docIds);
        Map<Long, Long> docToReqId = documents.stream()
                .filter(d -> d.getRequirementId() != null)
                .collect(Collectors.toMap(KnowledgeDocument::getId, KnowledgeDocument::getRequirementId, (a, b) -> a));

        Map<Long, KnowledgeSearchResponse.RequirementReference> resultMap = new HashMap<>();
        if (!docToReqId.isEmpty()) {
            List<Requirement> reqs = requirementMapper.selectBatchIds(docToReqId.values());
            Map<Long, Requirement> reqMap = reqs.stream().collect(Collectors.toMap(Requirement::getId, r -> r, (a, b) -> a));
            for (Map.Entry<Long, Long> entry : docToReqId.entrySet()) {
                Requirement req = reqMap.get(entry.getValue());
                if (req != null) resultMap.put(entry.getKey(), toRequirementReference(req));
            }
        }

        // Fallback: match by fileName for docs without requirementId
        Set<String> unmatchedFileNames = results.stream()
                .filter(sr -> !resultMap.containsKey(parseLong(sr.getEntity().get("document_id"))))
                .map(sr -> getString(sr.getEntity().get("file_name")))
                .filter(name -> name != null && !name.isBlank())
                .collect(Collectors.toSet());
        if (!unmatchedFileNames.isEmpty()) {
            List<Requirement> allWithAttachments = requirementMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Requirement>().isNotNull(Requirement::getAttachments));
            for (Requirement req : allWithAttachments) {
                if (req.getAttachments() == null) continue;
                for (var attachment : req.getAttachments()) {
                    if (attachment.getName() != null && unmatchedFileNames.stream()
                            .anyMatch(fn -> fn.equalsIgnoreCase(attachment.getName()))) {
                        // Find which docId this fileName belongs to
                        for (var sr : results) {
                            Long docId = parseLong(sr.getEntity().get("document_id"));
                            String fn = getString(sr.getEntity().get("file_name"));
                            if (!resultMap.containsKey(docId) && fn != null && fn.equalsIgnoreCase(attachment.getName())) {
                                resultMap.putIfAbsent(docId, toRequirementReference(req));
                            }
                        }
                    }
                }
            }
        }
        return resultMap;
    }

    private String buildProcessSummary(KnowledgeSearchRequest request, int candidateCount, List<KnowledgeSearchResponse.SearchResultItem> results) {
        String mode = request.getMode() == null ? "hybrid" : request.getMode();
        if (results.isEmpty()) {
            return String.format("系统按%s模式检索了知识库内容，但未找到与“%s”相关的文档片段。", mode, request.getQuery());
        }
        long relatedRequirementCount = results.stream().filter(item -> item.getRequirement() != null).count();
        return String.format(
                "系统按%s模式解析问题“%s”，在%s个候选片段中返回前%d条结果，其中%d条结果可追溯到需求流程附件。",
                mode,
                request.getQuery(),
                candidateCount,
                results.size(),
                relatedRequirementCount
        );
    }


    private KnowledgeSearchResponse.RequirementReference toRequirementReference(Requirement requirement) {
        return KnowledgeSearchResponse.RequirementReference.builder()
                .id(requirement.getId())
                .title(requirement.getTitle())
                .status(requirement.getStatus())
                .type(requirement.getType())
                .summary(buildRequirementSummary(requirement.getDescription()))
                .build();
    }

    private String buildRequirementSummary(String description) {
        if (description == null || description.isBlank()) {
            return "";
        }
        String plainText = description.replaceAll("<[^>]+>", "").replaceAll("\\s+", " ").trim();
        if (plainText.length() <= 80) {
            return plainText;
        }
        return plainText.substring(0, 80) + "...";
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
