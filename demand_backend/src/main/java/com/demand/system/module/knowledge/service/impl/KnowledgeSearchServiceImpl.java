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
                .processSummary(buildProcessSummary(request, results));

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
        String fileName = getString(entity.get("file_name"));
        return KnowledgeSearchResponse.SearchResultItem.builder()
                .chunkId(parseLong(entity.get("id")))
                .documentId(parseLong(entity.get("document_id")))
                .fileName(fileName)
                .sectionTitle(getString(entity.get("section_title")))
                .content(getString(entity.get("text")))
                .pageNum(parseInteger(entity.get("page_num")))
                .score((double) sr.getScore())
                .knowledgeBaseId(getString(entity.get("knowledge_base_id")))
                .requirement(findRequirementReference(parseLong(entity.get("document_id")), fileName))
                .build();
    }

    private String buildProcessSummary(KnowledgeSearchRequest request, List<KnowledgeSearchResponse.SearchResultItem> results) {
        String mode = request.getMode() == null ? "hybrid" : request.getMode();
        if (results.isEmpty()) {
            return String.format("系统按%s模式检索了知识库内容，但未找到与“%s”相关的文档片段。", mode, request.getQuery());
        }
        long relatedRequirementCount = results.stream().filter(item -> item.getRequirement() != null).count();
        return String.format(
                "系统按%s模式解析问题“%s”，在%s个候选片段中返回前%d条结果，其中%d条结果可追溯到需求流程附件。",
                mode,
                request.getQuery(),
                results.size(),
                results.size(),
                relatedRequirementCount
        );
    }

    private KnowledgeSearchResponse.RequirementReference findRequirementReference(Long documentId, String fileName) {
        if (documentId != null) {
            KnowledgeDocument document = knowledgeDocumentMapper.selectById(documentId);
            if (document != null && document.getRequirementId() != null) {
                Requirement requirement = requirementMapper.selectById(document.getRequirementId());
                if (requirement != null) {
                    return toRequirementReference(requirement);
                }
            }
        }

        if (fileName == null || fileName.isBlank()) {
            return null;
        }
        List<Requirement> requirements = requirementMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Requirement>()
                .isNotNull(Requirement::getAttachments)
                .orderByDesc(Requirement::getCreatedAt));
        for (Requirement requirement : requirements) {
            if (requirement.getAttachments() == null) {
                continue;
            }
            boolean matched = requirement.getAttachments().stream()
                    .map(RequirementAttachmentDTO::getName)
                    .filter(Objects::nonNull)
                    .anyMatch(fileName::equalsIgnoreCase);
            if (matched) {
                return toRequirementReference(requirement);
            }
        }
        return null;
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
