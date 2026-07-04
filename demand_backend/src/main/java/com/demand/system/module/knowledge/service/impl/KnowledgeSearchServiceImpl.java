package com.demand.system.module.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.demand.system.common.exception.BusinessException;
import com.demand.system.module.knowledge.config.KnowledgeConfig;
import com.demand.system.module.knowledge.dto.KnowledgeSearchRequest;
import com.demand.system.module.knowledge.dto.KnowledgeSearchResponse;
import com.demand.system.module.knowledge.entity.KnowledgeChunk;
import com.demand.system.module.knowledge.entity.KnowledgeDocument;
import com.demand.system.module.knowledge.mapper.KnowledgeChunkMapper;
import com.demand.system.module.knowledge.mapper.KnowledgeDocumentMapper;
import com.demand.system.module.knowledge.service.EmbeddingService;
import com.demand.system.module.knowledge.service.IntentRecognizer;
import com.demand.system.module.knowledge.service.KnowledgeSearchService;
import com.demand.system.module.knowledge.service.RagAnswerService;
import com.demand.system.module.knowledge.vectorstore.MilvusVectorStore;
import com.demand.system.module.requirement.entity.Requirement;
import com.demand.system.module.requirement.mapper.RequirementMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
public class KnowledgeSearchServiceImpl implements KnowledgeSearchService {
    private static final Logger log = LoggerFactory.getLogger(KnowledgeSearchServiceImpl.class);

    private final EmbeddingService embeddingService;
    private final MilvusVectorStore milvusVectorStore;
    private final KnowledgeConfig knowledgeConfig;
    private final RagAnswerService ragAnswerService;
    private final IntentRecognizer intentRecognizer;
    private final RequirementMapper requirementMapper;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final KnowledgeChunkMapper knowledgeChunkMapper;

    public KnowledgeSearchServiceImpl(EmbeddingService embeddingService,
                                     MilvusVectorStore milvusVectorStore,
                                     KnowledgeConfig knowledgeConfig,
                                     RagAnswerService ragAnswerService,
                                     IntentRecognizer intentRecognizer,
                                     RequirementMapper requirementMapper,
                                     KnowledgeDocumentMapper knowledgeDocumentMapper,
                                     KnowledgeChunkMapper knowledgeChunkMapper) {
        this.embeddingService = embeddingService;
        this.milvusVectorStore = milvusVectorStore;
        this.knowledgeConfig = knowledgeConfig;
        this.ragAnswerService = ragAnswerService;
        this.intentRecognizer = intentRecognizer;
        this.requirementMapper = requirementMapper;
        this.knowledgeDocumentMapper = knowledgeDocumentMapper;
        this.knowledgeChunkMapper = knowledgeChunkMapper;
    }

    @Override
    public KnowledgeSearchResponse search(KnowledgeSearchRequest request) {
        KnowledgeSearchResponse response = retrieve(request);
        String mode = request.getMode() != null ? request.getMode() : "hybrid";
        String query = request.getQuery();
        List<KnowledgeSearchResponse.ThinkingStep> thinkingSteps = buildThinkingSteps(query, mode, response);

        boolean shouldGenerateAnswer = !response.getResults().isEmpty() && ("rag".equals(mode) || request.getLlmModelId() != null);
        if (!shouldGenerateAnswer) {
            response.setThinkingSteps(thinkingSteps);
            return response;
        }

        try {
            String answer = ragAnswerService.generateAnswer(
                    query,
                    response.getResults(),
                    request.getKnowledgeBaseId(),
                    request.getLlmModelId()
            );
            response.setAnswer(answer);
        } catch (Exception e) {
            log.warn("RAG答案生成失败，仅返回检索结果", e);
            response.setAnswer(null);
        }
        response.setThinkingSteps(thinkingSteps);
        return response;
    }

    private List<KnowledgeSearchResponse.ThinkingStep> buildThinkingSteps(
            String query, String mode, KnowledgeSearchResponse response) {
        List<KnowledgeSearchResponse.ThinkingStep> steps = new ArrayList<>();
        steps.add(new KnowledgeSearchResponse.ThinkingStep(
                "query_parse", "问题解析", buildQueryParseDetail(query)));

        int resultCount = response.getResults().size();
        int uniqueDocs = (int) response.getResults().stream()
                .map(KnowledgeSearchResponse.SearchResultItem::getDocumentId)
                .filter(Objects::nonNull)
                .distinct()
                .count();
        steps.add(new KnowledgeSearchResponse.ThinkingStep(
                "retrieve", "文档检索",
                String.format("在知识库中检索到 %d 条相关片段，来自 %d 份文档", resultCount, uniqueDocs),
                resultCount > 0 ? Math.min(1.0, resultCount / 10.0) : 0.0));

        if ("hybrid".equals(mode)) {
            boolean reranked = response.getResults().stream()
                    .anyMatch(r -> r.getScore() != null && r.getScore() < 1.0);
            if (reranked) {
                steps.add(new KnowledgeSearchResponse.ThinkingStep(
                        "rerank", "智能排序",
                        String.format("使用重排序模型优化结果顺序，优先呈现最相关的 %d 条片段", Math.min(resultCount, 5))));
            }
        }

        String synthesizeDetail = response.getAnswer() != null
                ? String.format("已基于 %d 条检索片段生成回答（%d 字）", resultCount, response.getAnswer().length())
                : String.format("未使用 AI 模型，共检索到 %d 条相关片段", resultCount);
        steps.add(new KnowledgeSearchResponse.ThinkingStep(
                "synthesize", response.getAnswer() != null ? "生成回答" : "检索摘要", synthesizeDetail));

        return steps;
    }

    @Override
    public SseEmitter streamSearch(KnowledgeSearchRequest request) {
        SseEmitter emitter = new SseEmitter(180_000L);
        CompletableFuture.runAsync(() -> {
            StringBuilder answer = new StringBuilder();
            String query = request.getQuery();
            String mode = request.getMode() != null ? request.getMode() : "hybrid";

            // Step 0: 意图识别（不阻塞检索，异常降级）
            IntentRecognizer.IntentResult intentResult = null;
            try {
                intentResult = intentRecognizer.recognize(query);
                log.info("意图识别: query={}, intent={}, confidence={}",
                        query, intentResult.intent(), intentResult.confidence());
            } catch (Exception e) {
                log.warn("意图识别失败，跳过: {}", e.getMessage());
            }
            final IntentRecognizer.IntentResult finalIntent = intentResult;

            List<KnowledgeSearchResponse.ThinkingStep> thinkingSteps = new ArrayList<>();
            try {
                // Step 1: 问题解析
                thinkingSteps.add(new KnowledgeSearchResponse.ThinkingStep(
                        "query_parse",
                        "问题解析",
                        buildQueryParseDetail(query)
                ));

                // Step 2: 检索
                KnowledgeSearchResponse response = retrieve(request);
                emitter.send(SseEmitter.event().name("results").data(response));

                // 设置意图识别结果
                if (finalIntent != null) {
                    response.setQuestionIntent(finalIntent.intent());
                    response.setIntentConfidence(finalIntent.confidence());
                }

                // 构建角标引用列表
                response.setCitations(buildCitationReferences(response.getResults()));

                int resultCount = response.getResults().size();
                int uniqueDocs = (int) response.getResults().stream()
                        .map(KnowledgeSearchResponse.SearchResultItem::getDocumentId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .count();

                thinkingSteps.add(new KnowledgeSearchResponse.ThinkingStep(
                        "retrieve",
                        "文档检索",
                        String.format("在知识库中检索到 %d 条相关片段，来自 %d 份文档", resultCount, uniqueDocs),
                        resultCount > 0 ? Math.min(1.0, resultCount / 10.0) : 0.0
                ));

                // Step 3: Rerank（仅混合检索）
                if ("hybrid".equals(mode)) {
                    boolean hasReranked = response.getResults().stream()
                            .anyMatch(r -> r.getScore() != null && r.getScore() < 1.0);
                    if (hasReranked) {
                        thinkingSteps.add(new KnowledgeSearchResponse.ThinkingStep(
                                "rerank",
                                "智能排序",
                                String.format("使用重排序模型优化结果顺序，优先呈现最相关的 %d 条片段", Math.min(resultCount, 5))
                        ));
                    }
                }

                // Step 4: 生成回答
                boolean shouldGenerateAnswer = !response.getResults().isEmpty() && ("rag".equals(mode) || request.getLlmModelId() != null);
                if (shouldGenerateAnswer) {
                    thinkingSteps.add(new KnowledgeSearchResponse.ThinkingStep(
                            "synthesize",
                            "生成回答",
                            String.format("基于检索结果生成回答%s", request.getLlmModelId() != null ? "（使用 AI 模型）" : "")
                    ));

                    ragAnswerService.streamAnswer(
                            query,
                            response.getResults(),
                            request.getKnowledgeBaseId(),
                            request.getLlmModelId(),
                            token -> {
                                if (token == null || token.isEmpty()) {
                                    return;
                                }
                                try {
                                    answer.append(token);
                                    emitter.send(SseEmitter.event().name("delta").data(token));
                                } catch (Exception e) {
                                    throw new RuntimeException(e);
                                }
                            }
                    );
                    response.setAnswer(answer.toString());

                    // 更新 synthesize 步骤为完成状态
                    KnowledgeSearchResponse.ThinkingStep last = thinkingSteps.get(thinkingSteps.size() - 1);
                    last.setDetail(String.format("已基于 %d 条检索片段生成回答（%d 字）", resultCount, answer.length()));
                } else {
                    thinkingSteps.add(new KnowledgeSearchResponse.ThinkingStep(
                            "synthesize",
                            "检索摘要",
                            String.format("未使用 AI 模型，共检索到 %d 条相关片段", resultCount)
                    ));
                }

                response.setThinkingSteps(thinkingSteps);
                emitter.send(SseEmitter.event().name("done").data(response));
                emitter.complete();
            } catch (Exception e) {
                log.warn("流式知识库检索失败", e);
                // 即使出错，也尝试返回已有的 thinkingSteps
                try {
                    emitter.send(SseEmitter.event().name("error").data(Map.of(
                            "message", e.getMessage() != null ? e.getMessage() : "流式检索失败"
                    )));
                } catch (Exception ignored) {
                    // The client may already have closed the stream.
                }
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    private String buildQueryParseDetail(String query) {
        // 分析问题特征，提取关键词
        List<String> terms = Arrays.stream(query.split("[\\s,，。；;:：/\\\\|]+"))
                .map(String::trim)
                .filter(t -> t.length() >= 2)
                .limit(5)
                .collect(Collectors.toList());
        if (terms.isEmpty()) {
            return String.format("已解析问题：%s", shortenText(query, 30));
        }
        return String.format("已解析问题，关键词：%s", String.join("、", terms));
    }

    private String shortenText(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "…";
    }

    private KnowledgeSearchResponse retrieve(KnowledgeSearchRequest request) {
        String mode = request.getMode() != null ? request.getMode() : "hybrid";
        // 优先级：请求参数 > 模型配置 > 全局配置
        int topK = request.getTopK() != null ? request.getTopK() : resolveTopK();
        String kbId = request.getKnowledgeBaseId() != null ? String.valueOf(request.getKnowledgeBaseId()) : null;

        List<KnowledgeSearchResponse.SearchResultItem> results;

        if ("semantic".equals(mode)) {
            float[] queryVector = embeddingService.embed(request.getQuery());
            results = semanticSearch(queryVector, kbId, topK);
        } else if ("keyword".equals(mode)) {
            results = keywordSearch(request.getQuery(), request.getKnowledgeBaseId(), topK);
        } else {
            float[] queryVector = embeddingService.embed(request.getQuery());
            results = hybridSearch(request.getQuery(), queryVector, kbId, topK);
        }

        return KnowledgeSearchResponse.builder()
                .results(results)
                .total(results.size())
                .processSummary(buildProcessSummary(request, results.size(), results))
                .build();
    }

    /**
     * 解析 TopK，优先级：模型配置 > 全局配置
     */
    private int resolveTopK() {
        var modelConfig = embeddingService.getDefaultModelConfig();
        if (modelConfig != null) {
            return modelConfig.searchTopK();
        }
        return knowledgeConfig.getSearchTopK();
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

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Reranker调用失败，降级使用向量检索结果", e);
            return candidates.stream()
                    .limit(topK)
                    .map(sr -> toResultItem(sr, buildDocumentRequirementMap(candidates.stream().limit(topK).collect(Collectors.toList()))))
                    .collect(Collectors.toList());
        }
    }

    private List<KnowledgeSearchResponse.SearchResultItem> keywordSearch(
            String query, Long knowledgeBaseId, int topK) {
        String normalizedQuery = normalizeKeyword(query);
        if (normalizedQuery.isBlank()) {
            return Collections.emptyList();
        }

        int candidateLimit = Math.min(Math.max(topK * 10, 50), 200);
        List<String> terms = tokenizeKeyword(normalizedQuery);
        Map<Long, KeywordCandidate> candidates = new LinkedHashMap<>();

        LambdaQueryWrapper<KnowledgeChunk> chunkWrapper = new LambdaQueryWrapper<>();
        if (knowledgeBaseId != null) {
            chunkWrapper.eq(KnowledgeChunk::getKnowledgeBaseId, knowledgeBaseId);
        }
        chunkWrapper.and(wrapper -> {
            wrapper.like(KnowledgeChunk::getContent, normalizedQuery)
                    .or()
                    .like(KnowledgeChunk::getSectionTitle, normalizedQuery);
            for (String term : terms) {
                wrapper.or().like(KnowledgeChunk::getContent, term)
                        .or()
                        .like(KnowledgeChunk::getSectionTitle, term);
            }
        });
        chunkWrapper.last("LIMIT " + candidateLimit);

        for (KnowledgeChunk chunk : knowledgeChunkMapper.selectList(chunkWrapper)) {
            candidates.putIfAbsent(chunk.getId(), new KeywordCandidate(chunk, false));
        }

        LambdaQueryWrapper<KnowledgeDocument> documentWrapper = new LambdaQueryWrapper<>();
        if (knowledgeBaseId != null) {
            documentWrapper.eq(KnowledgeDocument::getKnowledgeBaseId, knowledgeBaseId);
        }
        documentWrapper.and(wrapper -> {
            wrapper.like(KnowledgeDocument::getFileName, normalizedQuery);
            for (String term : terms) {
                wrapper.or().like(KnowledgeDocument::getFileName, term);
            }
        });
        documentWrapper.last("LIMIT 50");

        List<Long> fileMatchedDocIds = knowledgeDocumentMapper.selectList(documentWrapper).stream()
                .map(KnowledgeDocument::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (!fileMatchedDocIds.isEmpty()) {
            LambdaQueryWrapper<KnowledgeChunk> byDocumentWrapper = new LambdaQueryWrapper<>();
            byDocumentWrapper.in(KnowledgeChunk::getDocumentId, fileMatchedDocIds);
            if (knowledgeBaseId != null) {
                byDocumentWrapper.eq(KnowledgeChunk::getKnowledgeBaseId, knowledgeBaseId);
            }
            byDocumentWrapper.last("LIMIT " + candidateLimit);
            for (KnowledgeChunk chunk : knowledgeChunkMapper.selectList(byDocumentWrapper)) {
                candidates.putIfAbsent(chunk.getId(), new KeywordCandidate(chunk, true));
            }
        }

        if (candidates.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Long> docIds = candidates.values().stream()
                .map(candidate -> candidate.chunk.getDocumentId())
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, KnowledgeDocument> documentMap = knowledgeDocumentMapper.selectBatchIds(docIds).stream()
                .collect(Collectors.toMap(KnowledgeDocument::getId, d -> d, (a, b) -> a));
        Map<Long, KnowledgeSearchResponse.RequirementReference> requirementMap = buildDocumentRequirementMap(docIds);

        return candidates.values().stream()
                .map(candidate -> toResultItem(candidate, documentMap, requirementMap, normalizedQuery, terms))
                .sorted(Comparator.comparingDouble(KnowledgeSearchResponse.SearchResultItem::getScore).reversed())
                .limit(topK)
                .collect(Collectors.toList());
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

    private KnowledgeSearchResponse.SearchResultItem toResultItem(
            KeywordCandidate candidate,
            Map<Long, KnowledgeDocument> documentMap,
            Map<Long, KnowledgeSearchResponse.RequirementReference> requirementMap,
            String query,
            List<String> terms) {
        KnowledgeChunk chunk = candidate.chunk;
        KnowledgeDocument document = documentMap.get(chunk.getDocumentId());
        Long docId = chunk.getDocumentId();
        return KnowledgeSearchResponse.SearchResultItem.builder()
                .chunkId(chunk.getId())
                .documentId(docId)
                .fileName(document != null ? document.getFileName() : null)
                .sectionTitle(chunk.getSectionTitle())
                .content(chunk.getContent())
                .pageNum(chunk.getPageNum())
                .score(scoreKeywordCandidate(candidate, document, query, terms))
                .knowledgeBaseId(chunk.getKnowledgeBaseId() != null ? String.valueOf(chunk.getKnowledgeBaseId()) : null)
                .requirement(requirementMap.get(docId))
                .build();
    }

    private Map<Long, KnowledgeSearchResponse.RequirementReference> buildDocumentRequirementMap(
            List<MilvusVectorStore.SearchResult> results) {
        Set<Long> docIds = results.stream()
                .map(sr -> parseLong(sr.getEntity().get("document_id")))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return buildDocumentRequirementMap(docIds, results);
    }

    private Map<Long, KnowledgeSearchResponse.RequirementReference> buildDocumentRequirementMap(Collection<Long> docIds) {
        return buildDocumentRequirementMap(docIds, Collections.emptyList());
    }

    private Map<Long, KnowledgeSearchResponse.RequirementReference> buildDocumentRequirementMap(
            Collection<Long> docIds, List<MilvusVectorStore.SearchResult> results) {
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
        Set<String> unmatchedFileNames = documents.stream()
                .filter(document -> !resultMap.containsKey(document.getId()))
                .map(KnowledgeDocument::getFileName)
                .filter(name -> name != null && !name.isBlank())
                .collect(Collectors.toSet());
        if (!unmatchedFileNames.isEmpty()) {
            List<Requirement> allWithAttachments = requirementMapper.selectList(
                    new LambdaQueryWrapper<Requirement>().isNotNull(Requirement::getAttachments));
            for (Requirement req : allWithAttachments) {
                if (req.getAttachments() == null) continue;
                for (var attachment : req.getAttachments()) {
                    if (attachment.getName() != null && unmatchedFileNames.stream()
                            .anyMatch(fn -> fn.equalsIgnoreCase(attachment.getName()))) {
                        for (KnowledgeDocument document : documents) {
                            String fn = document.getFileName();
                            if (!resultMap.containsKey(document.getId()) && fn != null && fn.equalsIgnoreCase(attachment.getName())) {
                                resultMap.putIfAbsent(document.getId(), toRequirementReference(req));
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
            return String.format("系统按%s模式检索了知识库内容，但未找到与\"%s\"相关的文档片段。", mode, request.getQuery());
        }
        long relatedRequirementCount = results.stream().filter(item -> item.getRequirement() != null).count();
        return String.format(
                "系统按%s模式解析问题\"%s\"，在%s个候选片段中返回前%d条结果，其中%d条结果可追溯到需求流程附件。",
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

    private String normalizeKeyword(String query) {
        return query == null ? "" : query.replaceAll("\\s+", " ").trim();
    }

    private List<String> tokenizeKeyword(String query) {
        return Arrays.stream(query.split("[\\s,，。；;:：/\\\\|]+"))
                .map(String::trim)
                .filter(term -> term.length() >= 2)
                .distinct()
                .limit(8)
                .collect(Collectors.toList());
    }

    private double scoreKeywordCandidate(
            KeywordCandidate candidate,
            KnowledgeDocument document,
            String query,
            List<String> terms) {
        String content = lower(candidate.chunk.getContent());
        String sectionTitle = lower(candidate.chunk.getSectionTitle());
        String fileName = lower(document != null ? document.getFileName() : null);
        String lowerQuery = lower(query);

        double score = 0.1d;
        if (contains(content, lowerQuery)) score += 0.55d;
        if (contains(sectionTitle, lowerQuery)) score += 0.2d;
        if (contains(fileName, lowerQuery)) score += 0.3d;
        if (candidate.fileNameMatched) score += 0.15d;

        for (String term : terms) {
            String lowerTerm = lower(term);
            if (contains(content, lowerTerm)) score += 0.08d;
            if (contains(sectionTitle, lowerTerm)) score += 0.05d;
            if (contains(fileName, lowerTerm)) score += 0.06d;
        }
        return Math.min(score, 1.0d);
    }

    private boolean contains(String source, String target) {
        return source != null && target != null && !target.isBlank() && source.contains(target);
    }

    /**
     * 构建角标引用列表：按 documentId 分组，按相关度降序，分配连续角标序号 [1] [2] ...
     */
    private List<KnowledgeSearchResponse.CitationReference> buildCitationReferences(
            List<KnowledgeSearchResponse.SearchResultItem> results) {
        if (results == null || results.isEmpty()) {
            return List.of();
        }

        Map<Long, List<KnowledgeSearchResponse.SearchResultItem>> byDoc = results.stream()
                .filter(r -> r.getDocumentId() != null)
                .collect(Collectors.groupingBy(KnowledgeSearchResponse.SearchResultItem::getDocumentId));

        List<KnowledgeSearchResponse.CitationReference> refs = new ArrayList<>();
        for (Map.Entry<Long, List<KnowledgeSearchResponse.SearchResultItem>> entry : byDoc.entrySet()) {
            Long docId = entry.getKey();
            List<KnowledgeSearchResponse.SearchResultItem> items = entry.getValue();

            KnowledgeSearchResponse.SearchResultItem first = items.get(0);
            double maxScore = items.stream()
                    .mapToDouble(KnowledgeSearchResponse.SearchResultItem::getScore)
                    .max()
                    .orElse(0.0);

            refs.add(KnowledgeSearchResponse.CitationReference.builder()
                    .documentId(docId)
                    .fileName(first.getFileName())
                    .hitCount(items.size())
                    .maxScore(maxScore)
                    .build());
        }

        // 按 maxScore 降序
        refs.sort((a, b) -> Double.compare(b.getMaxScore(), a.getMaxScore()));

        // 重新连续编号
        for (int i = 0; i < refs.size(); i++) {
            refs.get(i).setIndex(i + 1);
        }

        return refs;
    }

    private String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private record ScoredCandidate(MilvusVectorStore.SearchResult result, double score) {}

    private record KeywordCandidate(KnowledgeChunk chunk, boolean fileNameMatched) {}
}
