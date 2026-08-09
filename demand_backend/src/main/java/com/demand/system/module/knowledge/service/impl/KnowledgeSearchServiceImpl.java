package com.demand.system.module.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.demand.system.common.exception.BusinessException;
import com.demand.system.module.knowledge.config.KnowledgeConfig;
import com.demand.system.module.knowledge.constant.KnowledgeSearchScope;
import com.demand.system.module.knowledge.dto.KnowledgeSearchRequest;
import com.demand.system.module.knowledge.dto.KnowledgeSearchResponse;
import com.demand.system.module.knowledge.entity.KnowledgeChunk;
import com.demand.system.module.knowledge.entity.KnowledgeDocument;
import com.demand.system.module.knowledge.llm.LlmGateway;
import com.demand.system.module.knowledge.mapper.KnowledgeChunkMapper;
import com.demand.system.module.knowledge.mapper.KnowledgeDocumentMapper;
import com.demand.system.module.knowledge.service.EmbeddingService;
import com.demand.system.module.knowledge.service.ImageUnderstandingService;
import com.demand.system.module.knowledge.service.IntentRecognizer;
import com.demand.system.module.knowledge.service.KnowledgeSearchService;
import com.demand.system.module.knowledge.service.RagAnswerService;
import com.demand.system.module.knowledge.vectorstore.MilvusVectorStore;
import com.demand.system.module.requirement.entity.Requirement;
import com.demand.system.module.requirement.service.RequirementService;
import com.demand.system.module.auth.security.SecurityUtils;
import com.demand.system.module.requirement.mapper.RequirementMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

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
    private final RequirementService requirementService;
    private final ImageUnderstandingService imageUnderstandingService;

    public KnowledgeSearchServiceImpl(EmbeddingService embeddingService,
                                     MilvusVectorStore milvusVectorStore,
                                     KnowledgeConfig knowledgeConfig,
                                     RagAnswerService ragAnswerService,
                                     IntentRecognizer intentRecognizer,
                                     RequirementMapper requirementMapper,
                                     KnowledgeDocumentMapper knowledgeDocumentMapper,
                                     KnowledgeChunkMapper knowledgeChunkMapper, RequirementService requirementService,
                                     ImageUnderstandingService imageUnderstandingService) {
        this.embeddingService = embeddingService;
        this.milvusVectorStore = milvusVectorStore;
        this.knowledgeConfig = knowledgeConfig;
        this.ragAnswerService = ragAnswerService;
        this.intentRecognizer = intentRecognizer;
        this.requirementMapper = requirementMapper;
        this.knowledgeDocumentMapper = knowledgeDocumentMapper;
        this.knowledgeChunkMapper = knowledgeChunkMapper;
        this.requirementService = requirementService;
        this.imageUnderstandingService = imageUnderstandingService;
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
            LlmGateway.ChatResult chatResult = ragAnswerService.generateAnswerWithReasoning(
                    query,
                    response.getResults(),
                    request.getKnowledgeBaseId(),
                    request.getLlmModelId()
            );
            if (chatResult != null) {
                response.setAnswer(chatResult.getContent());
                response.setReasoningContent(chatResult.getReasoningContent());
            }
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
        SearchScopeDecision scopes = resolveSearchScopes(request);

        List<KnowledgeSearchResponse.SearchResultItem> results;
        if (!scopes.includeKnowledgeBase() && !scopes.includeRequirementBody()) {
            results = Collections.emptyList();
        } else if ("semantic".equals(mode)) {
            float[] queryVector = embeddingService.embed(request.getQuery());
            results = semanticSearch(queryVector, kbId, topK,
                    scopes.includeKnowledgeBase(), scopes.includeRequirementBody());
        } else if ("keyword".equals(mode)) {
            results = keywordSearch(request.getQuery(), request.getKnowledgeBaseId(), topK,
                    scopes.includeKnowledgeBase(), scopes.includeRequirementBody());
        } else {
            float[] queryVector = embeddingService.embed(request.getQuery());
            results = hybridSearch(request.getQuery(), queryVector, kbId, topK,
                    scopes.includeKnowledgeBase(), scopes.includeRequirementBody());
        }

        // 在生成回答前做后端权限过滤，禁止无权工单正文进入检索结果或 LLM 上下文。
        results = filterVisibleRequirementResults(results, request.getRequesterId());

        KnowledgeSearchResponse response = KnowledgeSearchResponse.builder().results(results)
                .total(results.size())
                .processSummary(buildProcessSummary(request, results.size(), results))
                .citations(buildCitationReferences(results))
                .warnings(buildRetrievalWarnings(results))
                .build();
        return response;
    }

    private List<String> buildRetrievalWarnings(List<KnowledgeSearchResponse.SearchResultItem> results) {
        if (results == null || results.isEmpty() || imageUnderstandingService.enabled()) {
            return List.of();
        }
        Set<Long> documentIds = results.stream()
                .map(KnowledgeSearchResponse.SearchResultItem::getDocumentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (documentIds.isEmpty()) {
            return List.of();
        }
        Map<Long, KnowledgeDocument> bodyDocuments = knowledgeDocumentMapper.selectBatchIds(documentIds).stream()
                .filter(document -> "requirement_body".equals(document.getSourceType()))
                .collect(Collectors.toMap(KnowledgeDocument::getId, document -> document, (left, right) -> left));
        if (bodyDocuments.isEmpty()) {
            return List.of();
        }
        Set<Long> requirementIds = bodyDocuments.values().stream()
                .map(KnowledgeDocument::getRequirementId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (requirementIds.isEmpty()) {
            return List.of();
        }
        boolean hasBodyImage = requirementMapper.selectBatchIds(requirementIds).stream()
                .map(Requirement::getDescription)
                .filter(Objects::nonNull)
                .anyMatch(description -> description.toLowerCase(Locale.ROOT).contains("<img"));
        boolean hasImageEvidence = results.stream()
                .filter(item -> bodyDocuments.containsKey(item.getDocumentId()))
                .anyMatch(item -> item.getImageFileId() != null);
        if (hasBodyImage && !hasImageEvidence) {
            return List.of("已找到相关工单正文，但当前未配置图片理解模型，图片中的文字和语义暂未完成处理。请在“模型配置-模型应用”中配置 vision 模型后重建索引。");
        }
        return List.of();
    }

    private SearchScopeDecision resolveSearchScopes(KnowledgeSearchRequest request) {
        Collection<String> requestedScopes = request.getSearchScopes();
        Set<String> explicit = KnowledgeSearchScope.normalize(requestedScopes);
        if (requestedScopes == null || requestedScopes.isEmpty()) {
            // 旧接口行为：知识库检索自动包含工单正文。
            return new SearchScopeDecision(true, true);
        }
        // 非空但全部非法时，不扩大检索范围，避免误把请求当成兼容旧接口。
        return new SearchScopeDecision(explicit.contains(KnowledgeSearchScope.KNOWLEDGE_BASE),
                explicit.contains(KnowledgeSearchScope.REQUIREMENT_BODY));
    }

    private boolean isAllowedMilvusSource(MilvusVectorStore.SearchResult result,
                                           Map<Long, KnowledgeDocument> documents,
                                           boolean includeKnowledgeBase,
                                           boolean includeRequirementBody) {
        Long documentId = parseLong(result.getEntity().get("document_id"));
        KnowledgeDocument document = documents.get(documentId);
        if (document == null || !"indexed".equalsIgnoreCase(document.getStatus())) {
            return false;
        }
        boolean body = "requirement_body".equals(document.getSourceType());
        return body ? includeRequirementBody : includeKnowledgeBase;
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

    /** 指定知识库检索时，额外召回全局工单正文；null 表示全部知识库。 */
    private List<MilvusVectorStore.SearchResult> searchMilvusIncludingRequirementBodies(
            float[] queryVector, String knowledgeBaseId, int topK,
            boolean includeKnowledgeBase, boolean includeRequirementBody) {
        List<MilvusVectorStore.SearchResult> selected = includeKnowledgeBase
                ? milvusVectorStore.search(queryVector, knowledgeBaseId, Math.min(Math.max(topK * 5, 50), 300))
                : List.of();
        List<MilvusVectorStore.SearchResult> globalCandidates = includeRequirementBody
                ? milvusVectorStore.search(queryVector, null, Math.min(Math.max(topK * 10, 100), 500))
                : List.of();
        Set<Long> documentIds = Stream.concat(selected.stream(), globalCandidates.stream())
                .map(result -> parseLong(result.getEntity().get("document_id")))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, KnowledgeDocument> documents = knowledgeDocumentMapper.selectBatchIds(documentIds).stream()
                .collect(Collectors.toMap(KnowledgeDocument::getId, document -> document, (left, right) -> left));
        Map<String, MilvusVectorStore.SearchResult> merged = new LinkedHashMap<>();
        for (MilvusVectorStore.SearchResult result : selected) {
            merged.putIfAbsent(String.valueOf(result.getEntity().get("id")), result);
        }
        for (MilvusVectorStore.SearchResult result : globalCandidates) {
            Long documentId = parseLong(result.getEntity().get("document_id"));
            KnowledgeDocument document = documents.get(documentId);
            if (document != null && includeRequirementBody && "requirement_body".equals(document.getSourceType())) {
                merged.putIfAbsent(String.valueOf(result.getEntity().get("id")), result);
            }
        }
        return merged.values().stream()
                .filter(result -> isAllowedMilvusSource(result, documents, includeKnowledgeBase, includeRequirementBody))
                .sorted(Comparator.comparingDouble(MilvusVectorStore.SearchResult::getScore).reversed())
                .limit(topK)
                .toList();
    }
    private List<KnowledgeSearchResponse.SearchResultItem> semanticSearch(
            float[] queryVector, String knowledgeBaseId, int topK,
            boolean includeKnowledgeBase, boolean includeRequirementBody) {
        List<MilvusVectorStore.SearchResult> milvusResults =
                searchMilvusIncludingRequirementBodies(queryVector, knowledgeBaseId, topK,
                        includeKnowledgeBase, includeRequirementBody);
        Map<Long, KnowledgeSearchResponse.RequirementReference> reqMap = buildDocumentRequirementMap(milvusResults);
        Map<String, KnowledgeChunk> chunkMap = buildChunkMetadataMap(milvusResults);
        return milvusResults.stream()
                .map(sr -> toResultItem(sr, reqMap, chunkMap))
                .collect(Collectors.toList());
    }

    private List<KnowledgeSearchResponse.SearchResultItem> hybridSearch(
            String query, float[] queryVector, String knowledgeBaseId, int topK,
            boolean includeKnowledgeBase, boolean includeRequirementBody) {
        int candidateSize = Math.min(topK * 5, 100);
        List<MilvusVectorStore.SearchResult> candidates =
                searchMilvusIncludingRequirementBodies(queryVector, knowledgeBaseId, candidateSize,
                        includeKnowledgeBase, includeRequirementBody);

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

            List<MilvusVectorStore.SearchResult> scoredResults = scored.stream().map(ScoredCandidate::result).toList();
            Map<Long, KnowledgeSearchResponse.RequirementReference> reqMap = buildDocumentRequirementMap(scoredResults);
            Map<String, KnowledgeChunk> chunkMap = buildChunkMetadataMap(scoredResults);
            return scored.stream()
                    .limit(topK)
                    .map(s -> {
                        KnowledgeSearchResponse.SearchResultItem item = toResultItem(s.result, reqMap, chunkMap);
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
                    .map(sr -> toResultItem(sr, buildDocumentRequirementMap(candidates.stream().limit(topK).collect(Collectors.toList())),
                            buildChunkMetadataMap(candidates.stream().limit(topK).collect(Collectors.toList()))))
                    .collect(Collectors.toList());
        }
    }

    private List<KnowledgeSearchResponse.SearchResultItem> keywordSearch(
            String query, Long knowledgeBaseId, int topK,
            boolean includeKnowledgeBase, boolean includeRequirementBody) {
        String normalizedQuery = normalizeKeyword(query);
        if (normalizedQuery.isBlank()) {
            return Collections.emptyList();
        }

        int candidateLimit = Math.min(Math.max(topK * 10, 50), 200);
        List<String> terms = tokenizeKeyword(normalizedQuery);
        Map<Long, KeywordCandidate> candidates = new LinkedHashMap<>();

        LambdaQueryWrapper<KnowledgeChunk> chunkWrapper = new LambdaQueryWrapper<>();
        if (!includeKnowledgeBase) {
            chunkWrapper.eq(KnowledgeChunk::getId, -1L);
        } else if (knowledgeBaseId != null) {
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
        // 工单正文是独立来源，可跨知识库检索；正文引用后续按权限过滤。
        if (includeRequirementBody) {
            for (KnowledgeChunk chunk : knowledgeChunkMapper.searchRequirementBodyChunks(normalizedQuery, terms, candidateLimit)) {
                candidates.putIfAbsent(chunk.getId(), new KeywordCandidate(chunk, false));
            }
        }
        LambdaQueryWrapper<KnowledgeDocument> documentWrapper = new LambdaQueryWrapper<>();
        if (!includeKnowledgeBase) {
            documentWrapper.eq(KnowledgeDocument::getId, -1L);
        } else if (knowledgeBaseId != null) {
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
        List<Long> bodyFileMatchedDocIds = includeRequirementBody ? knowledgeDocumentMapper.selectList(new LambdaQueryWrapper<KnowledgeDocument>()
                .eq(KnowledgeDocument::getSourceType, "requirement_body")
                .and(wrapper -> {
                    wrapper.like(KnowledgeDocument::getFileName, normalizedQuery);
                    for (String term : terms) {
                        wrapper.or().like(KnowledgeDocument::getFileName, term);
                    }
                })
                .last("LIMIT 50")).stream()
                .map(KnowledgeDocument::getId)
                .filter(Objects::nonNull)
                .toList() : List.of();
        fileMatchedDocIds = new ArrayList<>(fileMatchedDocIds);
        fileMatchedDocIds.addAll(bodyFileMatchedDocIds);
        if (!fileMatchedDocIds.isEmpty()) {
            LambdaQueryWrapper<KnowledgeChunk> byDocumentWrapper = new LambdaQueryWrapper<>();
            byDocumentWrapper.in(KnowledgeChunk::getDocumentId, fileMatchedDocIds);
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
        candidates.entrySet().removeIf(entry -> {
            KnowledgeDocument document = documentMap.get(entry.getValue().chunk.getDocumentId());
            if (document == null || !"indexed".equalsIgnoreCase(document.getStatus())) {
                return true;
            }
            boolean body = "requirement_body".equals(document.getSourceType());
            return body ? !includeRequirementBody : !includeKnowledgeBase;
        });
        if (candidates.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, KnowledgeSearchResponse.RequirementReference> requirementMap = buildDocumentRequirementMap(
                candidates.values().stream().map(candidate -> candidate.chunk.getDocumentId()).filter(Objects::nonNull).collect(Collectors.toSet()));

        return candidates.values().stream()
                .map(candidate -> toResultItem(candidate, documentMap, requirementMap, normalizedQuery, terms))
                .sorted(Comparator.comparingDouble(KnowledgeSearchResponse.SearchResultItem::getScore).reversed())
                .limit(topK)
                .collect(Collectors.toList());
    }

    private KnowledgeSearchResponse.SearchResultItem toResultItem(
            MilvusVectorStore.SearchResult sr,
            Map<Long, KnowledgeSearchResponse.RequirementReference> requirementMap,
            Map<String, KnowledgeChunk> chunkMetadataMap) {
        Map<String, Object> entity = sr.getEntity();
        Long docId = parseLong(entity.get("document_id"));
        String vectorId = getString(entity.get("id"));
        KnowledgeChunk metadata = chunkMetadataMap.get(vectorId);
        return KnowledgeSearchResponse.SearchResultItem.builder()
                .chunkId(metadata != null ? metadata.getId() : parseLong(entity.get("id")))
                .documentId(docId)
                .fileName(getString(entity.get("file_name")))
                .sectionTitle(getString(entity.get("section_title")))
                .content(getString(entity.get("text")))
                .pageNum(parseInteger(entity.get("page_num")))
                .score((double) sr.getScore())
                .knowledgeBaseId(getString(entity.get("knowledge_base_id")))
                .requirement(requirementMap.get(docId))
                .imageFileId(metadata != null ? metadata.getSourceRefId() : null)
                .imagePosition(metadata != null ? metadata.getSourcePosition() : null)
                .focus(metadata != null && metadata.getSourceRefId() != null ? "image" : null)
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
                .imageFileId(chunk.getSourceRefId())
                .imagePosition(chunk.getSourcePosition())
                .focus(chunk.getSourceRefId() != null ? "image" : null)
                .build();
    }

    private Map<String, KnowledgeChunk> buildChunkMetadataMap(List<MilvusVectorStore.SearchResult> results) {
        if (results == null || results.isEmpty()) {
            return Collections.emptyMap();
        }
        List<String> vectorIds = results.stream()
                .map(result -> getString(result.getEntity().get("id")))
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .toList();
        if (vectorIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return knowledgeChunkMapper.selectByVectorIds(vectorIds).stream()
                .filter(chunk -> chunk.getVectorId() != null)
                .collect(Collectors.toMap(KnowledgeChunk::getVectorId, chunk -> chunk, (left, right) -> left));
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

    private List<KnowledgeSearchResponse.SearchResultItem> filterVisibleRequirementResults(
            List<KnowledgeSearchResponse.SearchResultItem> results, Long requesterId) {
        if (results == null || results.isEmpty()) {
            return List.of();
        }
        Set<Long> documentIds = results.stream()
                .map(KnowledgeSearchResponse.SearchResultItem::getDocumentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (documentIds.isEmpty()) {
            return results;
        }
        Map<Long, KnowledgeDocument> documentMap = knowledgeDocumentMapper.selectBatchIds(documentIds).stream()
                .collect(Collectors.toMap(KnowledgeDocument::getId, document -> document, (left, right) -> left));
        Map<Long, Requirement> requirementMap = documentMap.values().stream()
                .map(KnowledgeDocument::getRequirementId)
                .filter(Objects::nonNull)
                .distinct()
                .map(requirementMapper::selectById)
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(Requirement::getId, requirement -> requirement, (left, right) -> left));
        Long userId = requesterId != null ? requesterId : SecurityUtils.getCurrentUserId();
        return results.stream()
                .filter(result -> {
                    KnowledgeDocument document = documentMap.get(result.getDocumentId());
                    if (document == null || !"indexed".equalsIgnoreCase(document.getStatus())) {
                        // Milvus 中可能短暂残留已删除、失败或重建中的旧向量；这些文档不能进入最终上下文。
                        return false;
                    }
                    if (document.getRequirementId() == null) {
                        return true;
                    }
                    return requirementService.canViewForSearch(requirementMap.get(document.getRequirementId()), userId);
                })
                .toList();
    }
    private String buildProcessSummary(KnowledgeSearchRequest request, int candidateCount, List<KnowledgeSearchResponse.SearchResultItem> results) {
        String mode = request.getMode() == null ? "hybrid" : request.getMode();
        if (results.isEmpty()) {
            return String.format("系统按%s模式检索了知识库内容，但未找到与\"%s\"相关的文档片段。", mode, request.getQuery());
        }
        long relatedRequirementCount = results.stream().filter(item -> item.getRequirement() != null).count();
        return String.format(
                "系统按%s模式解析问题\"%s\"，在%s个候选片段中返回前%d条结果，其中%d条结果可追溯到工单正文或附件。",
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
                .requirementNo(requirement.getRequirementNo())
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
                    .mapToDouble(item -> item.getScore() == null ? 0.0 : item.getScore())
                    .max()
                    .orElse(0.0);
            KnowledgeDocument document = knowledgeDocumentMapper.selectById(docId);
            KnowledgeSearchResponse.RequirementReference requirement = first.getRequirement();

            KnowledgeSearchResponse.CitationReference.Builder builder = KnowledgeSearchResponse.CitationReference.builder()
                    .documentId(docId)
                    .fileName(first.getFileName())
                    .hitCount(items.size())
                    .maxScore(maxScore)
                    .knowledgeBaseId(first.getKnowledgeBaseId())
                    .sources(items.stream().map(KnowledgeSearchResponse.SearchResultItem::getSectionTitle)
                            .filter(Objects::nonNull).filter(value -> !value.isBlank()).distinct().limit(8).toList())
                    .contentType(resolveCitationContentType(items))
                    .imageFileId(bestImageItem(items) != null ? bestImageItem(items).getImageFileId() : null)
                    .imagePosition(bestImageItem(items) != null ? bestImageItem(items).getImagePosition() : null)
                    .focus(bestImageItem(items) != null && bestImageItem(items).getImageFileId() != null ? "image" : null);
            if (document != null) {
                builder.sourceType(document.getSourceType())
                        .requirementId(document.getRequirementId());
            }
            if (requirement != null) {
                builder.requirementNo(requirement.getRequirementNo())
                        .requirementTitle(requirement.getTitle());
            }
            refs.add(builder.build());
        }

        // 按 maxScore 降序
        refs.sort((a, b) -> Double.compare(b.getMaxScore(), a.getMaxScore()));

        // 重新连续编号
        for (int i = 0; i < refs.size(); i++) {
            refs.get(i).setIndex(i + 1);
        }

        return refs;
    }

    private KnowledgeSearchResponse.SearchResultItem bestImageItem(List<KnowledgeSearchResponse.SearchResultItem> items) {
        return items.stream()
                .filter(item -> item.getImageFileId() != null)
                .max(Comparator.comparingDouble(item -> item.getScore() == null ? 0d : item.getScore()))
                .orElse(null);
    }

    private String resolveCitationContentType(List<KnowledgeSearchResponse.SearchResultItem> items) {
        boolean ocr = items.stream().map(KnowledgeSearchResponse.SearchResultItem::getSectionTitle)
                .filter(Objects::nonNull).anyMatch(title -> title.contains("OCR"));
        boolean caption = items.stream().map(KnowledgeSearchResponse.SearchResultItem::getSectionTitle)
                .filter(Objects::nonNull).anyMatch(title -> title.contains("图片理解"));
        boolean body = items.stream().map(KnowledgeSearchResponse.SearchResultItem::getSectionTitle)
                .filter(Objects::nonNull).anyMatch(title -> title.equals("工单正文"));
        if (ocr && (caption || body)) return "body_image";
        if (caption && body) return "body_image";
        if (ocr) return "image_ocr";
        if (caption) return "image_caption";
        return "body";
    }

    private String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private record SearchScopeDecision(boolean includeKnowledgeBase, boolean includeRequirementBody) {}

    private record ScoredCandidate(MilvusVectorStore.SearchResult result, double score) {}

    private record KeywordCandidate(KnowledgeChunk chunk, boolean fileNameMatched) {}
}
