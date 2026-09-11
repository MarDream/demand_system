package com.demand.system.module.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.demand.system.common.config.SseExecutorConfig;
import com.demand.system.common.exception.BusinessException;
import com.demand.system.module.knowledge.config.KnowledgeConfig;
import com.demand.system.module.knowledge.constant.KnowledgeSearchScope;
import com.demand.system.module.knowledge.dto.KnowledgeSearchRequest;
import com.demand.system.module.knowledge.dto.KnowledgeSearchResponse;
import com.demand.system.module.knowledge.dto.QueryRewriteResult;
import com.demand.system.module.knowledge.entity.KnowledgeChunk;
import com.demand.system.module.knowledge.entity.KnowledgeDocument;
import com.demand.system.module.knowledge.llm.LlmGateway;
import com.demand.system.module.knowledge.mapper.KnowledgeChunkMapper;
import com.demand.system.module.knowledge.mapper.KnowledgeDocumentMapper;
import com.demand.system.module.knowledge.service.EmbeddingService;
import com.demand.system.module.knowledge.service.ImageUnderstandingService;
import com.demand.system.module.knowledge.service.KnowledgeSearchService;
import com.demand.system.module.knowledge.service.QueryRewriteService;
import com.demand.system.module.knowledge.service.RagAnswerService;
import com.demand.system.module.knowledge.vectorstore.MilvusVectorStore;
import com.demand.system.module.requirement.entity.Requirement;
import com.demand.system.module.requirement.mapper.RequirementMapper;
import com.demand.system.module.requirement.service.RequirementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class KnowledgeSearchServiceImpl implements KnowledgeSearchService {
    private static final Logger log = LoggerFactory.getLogger(KnowledgeSearchServiceImpl.class);

    /** RRF（Reciprocal Rank Fusion）常数，业界标准取值 60 */
    private static final double RRF_K = 60d;
    /** 文件名兜底匹配的候选文件名上限，超过则跳过兜底，避免大范围 LIKE 扫描 */
    private static final int MAX_FILENAME_FALLBACK_MATCHES = 20;

    private final EmbeddingService embeddingService;
    private final MilvusVectorStore milvusVectorStore;
    private final KnowledgeConfig knowledgeConfig;
    private final RagAnswerService ragAnswerService;
    private final QueryRewriteService queryRewriteService;
    private final RequirementMapper requirementMapper;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final KnowledgeChunkMapper knowledgeChunkMapper;
    private final RequirementService requirementService;
    private final ImageUnderstandingService imageUnderstandingService;
    private final Executor sseExecutor;

    public KnowledgeSearchServiceImpl(EmbeddingService embeddingService,
                                     MilvusVectorStore milvusVectorStore,
                                     KnowledgeConfig knowledgeConfig,
                                     RagAnswerService ragAnswerService,
                                     QueryRewriteService queryRewriteService,
                                     RequirementMapper requirementMapper,
                                     KnowledgeDocumentMapper knowledgeDocumentMapper,
                                     KnowledgeChunkMapper knowledgeChunkMapper,
                                     RequirementService requirementService,
                                     ImageUnderstandingService imageUnderstandingService,
                                     @Qualifier(SseExecutorConfig.SSE_TASK_EXECUTOR) Executor sseExecutor) {
        this.embeddingService = embeddingService;
        this.milvusVectorStore = milvusVectorStore;
        this.knowledgeConfig = knowledgeConfig;
        this.ragAnswerService = ragAnswerService;
        this.queryRewriteService = queryRewriteService;
        this.requirementMapper = requirementMapper;
        this.knowledgeDocumentMapper = knowledgeDocumentMapper;
        this.knowledgeChunkMapper = knowledgeChunkMapper;
        this.requirementService = requirementService;
        this.imageUnderstandingService = imageUnderstandingService;
        this.sseExecutor = sseExecutor;
    }

    // ==================== 对外入口 ====================

    @Override
    public KnowledgeSearchResponse search(KnowledgeSearchRequest request) {
        KnowledgeSearchResponse response = retrieve(request);
        String query = request.getQuery();
        String mode = normalizeMode(request.getMode());
        List<KnowledgeSearchResponse.ThinkingStep> thinkingSteps = buildThinkingSteps(query, mode, response);

        boolean shouldGenerateAnswer = !response.getResults().isEmpty()
                && ("rag".equals(mode) || request.getLlmModelId() != null);
        if (!shouldGenerateAnswer) {
            response.setThinkingSteps(thinkingSteps);
            return response;
        }

        try {
            List<KnowledgeSearchResponse.SearchResultItem> contextItems = selectContextResults(response.getResults());
            LlmGateway.ChatResult chatResult = ragAnswerService.generateAnswerWithReasoning(
                    query,
                    contextItems,
                    request.getKnowledgeBaseId(),
                    request.getLlmModelId(),
                    request.getHistory()
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

    @Override
    public SseEmitter streamSearch(KnowledgeSearchRequest request) {
        SseEmitter emitter = new SseEmitter(180_000L);
        sseExecutor.execute(() -> {
            StringBuilder answer = new StringBuilder();
            StringBuilder reasoning = new StringBuilder();
            String query = request.getQuery();
            String mode = normalizeMode(request.getMode());

            try {
                // Step 1: 查询改写（结合多轮历史消解指代，同时产出关键词/意图/追问推荐；失败自动降级）
                QueryRewriteResult rewrite = queryRewriteService.rewrite(query, request.getHistory());
                List<KnowledgeSearchResponse.ThinkingStep> thinkingSteps = new ArrayList<>();
                thinkingSteps.add(new KnowledgeSearchResponse.ThinkingStep(
                        "query_parse", "问题解析", buildQueryParseDetail(query, rewrite)));

                // Step 2: 用改写后的独立问题执行检索
                KnowledgeSearchResponse response = retrieve(copyWithQuery(request, rewrite.safeStandaloneQuery()));

                // 先补齐意图 / 追问推荐再下发 results，保证前端首帧数据完整（citations 已在 retrieve 组装）
                response.setQuestionIntent(rewrite.intent());
                response.setIntentConfidence(rewrite.confidence());
                response.setSuggestedFollowUps(rewrite.followUps());
                emitter.send(SseEmitter.event().name("results").data(response));

                int resultCount = response.getResults().size();
                int uniqueDocs = countUniqueDocuments(response.getResults());
                thinkingSteps.add(new KnowledgeSearchResponse.ThinkingStep(
                        "retrieve",
                        "文档检索",
                        String.format("在知识库中检索到 %d 条相关片段，来自 %d 份文档", resultCount, uniqueDocs),
                        resultCount > 0 ? Math.min(1.0, resultCount / 10.0) : 0.0
                ));

                // Step 3: 重排序说明（降级时 warnings 已如实提示，这里不再虚构“已重排”）
                if ("hybrid".equals(mode) && !hasRerankDegraded(response)) {
                    thinkingSteps.add(new KnowledgeSearchResponse.ThinkingStep(
                            "rerank",
                            "智能排序",
                            String.format("使用重排序模型优化结果顺序，优先呈现最相关的 %d 条片段", Math.min(resultCount, 5))
                    ));
                }

                // Step 4: 流式生成回答（token / 深度思考 / 真实 token 用量分别推送）
                boolean shouldGenerateAnswer = !response.getResults().isEmpty()
                        && ("rag".equals(mode) || request.getLlmModelId() != null);
                if (shouldGenerateAnswer) {
                    List<KnowledgeSearchResponse.SearchResultItem> contextItems = selectContextResults(response.getResults());
                    thinkingSteps.add(new KnowledgeSearchResponse.ThinkingStep(
                            "synthesize",
                            "生成回答",
                            String.format("基于 %d 条检索片段流式生成回答%s", contextItems.size(),
                                    request.getLlmModelId() != null ? "（使用指定模型）" : "")
                    ));

                    ragAnswerService.streamAnswerWithReasoning(
                            rewrite.safeStandaloneQuery(),
                            contextItems,
                            request.getKnowledgeBaseId(),
                            request.getLlmModelId(),
                            request.getHistory(),
                            token -> {
                                if (token == null || token.isEmpty()) return;
                                try {
                                    answer.append(token);
                                    emitter.send(SseEmitter.event().name("delta").data(token));
                                } catch (Exception e) {
                                    throw new RuntimeException(e);
                                }
                            },
                            reasoningToken -> {
                                if (reasoningToken == null || reasoningToken.isEmpty()) return;
                                try {
                                    reasoning.append(reasoningToken);
                                    emitter.send(SseEmitter.event().name("reasoningDelta").data(reasoningToken));
                                } catch (Exception e) {
                                    throw new RuntimeException(e);
                                }
                            },
                            usage -> {
                                try {
                                    emitter.send(SseEmitter.event().name("usage").data(usage));
                                } catch (Exception ignored) {
                                    // usage 推送失败不影响主流程
                                }
                            }
                    );
                    response.setAnswer(answer.toString());
                    if (reasoning.length() > 0) {
                        response.setReasoningContent(reasoning.toString());
                    }

                    KnowledgeSearchResponse.ThinkingStep last = thinkingSteps.get(thinkingSteps.size() - 1);
                    last.setDetail(String.format("已基于 %d 条检索片段生成回答（%d 字）", contextItems.size(), answer.length()));
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
                try {
                    emitter.send(SseEmitter.event().name("error").data(Map.of(
                            "message", e.getMessage() != null ? e.getMessage() : "流式检索失败"
                    )));
                } catch (Exception ignored) {
                    // 客户端可能已断开
                }
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    // ==================== 检索编排 ====================

    @Override
    public KnowledgeSearchResponse retrieve(KnowledgeSearchRequest request) {
        String mode = normalizeMode(request.getMode());
        // 优先级：请求参数 > 模型配置 > 全局配置
        int topK = request.getTopK() != null ? request.getTopK() : resolveTopK();
        String kbId = request.getKnowledgeBaseId() != null ? String.valueOf(request.getKnowledgeBaseId()) : null;
        SearchScopeDecision scopes = resolveSearchScopes(request);

        // 超采样召回，权限过滤后回补至 topK
        int candidateTopK = Math.min(Math.max(topK * 2, topK), 100);
        RetrievalOutcome outcome;
        if (!scopes.includeKnowledgeBase() && !scopes.includeRequirementBody()) {
            outcome = new RetrievalOutcome(Collections.emptyList(), false);
        } else if ("semantic".equals(mode)) {
            float[] queryVector = embeddingService.embed(request.getQuery());
            outcome = new RetrievalOutcome(
                    semanticSearch(queryVector, kbId, candidateTopK,
                            scopes.includeKnowledgeBase(), scopes.includeRequirementBody()),
                    false);
        } else if ("keyword".equals(mode)) {
            outcome = new RetrievalOutcome(
                    keywordSearch(request.getQuery(), request.getKnowledgeBaseId(), candidateTopK,
                            scopes.includeKnowledgeBase(), scopes.includeRequirementBody()),
                    false);
        } else {
            float[] queryVector = embeddingService.embed(request.getQuery());
            outcome = hybridSearch(request.getQuery(), queryVector, kbId, candidateTopK,
                    scopes.includeKnowledgeBase(), scopes.includeRequirementBody());
        }

        // 在生成回答前做后端权限过滤，禁止无权工单正文进入检索结果或 LLM 上下文。
        List<KnowledgeSearchResponse.SearchResultItem> results = filterVisibleRequirementResults(
                        outcome.items(), request.getRequesterId())
                .stream()
                .limit(topK)
                .collect(Collectors.toList());

        List<String> warnings = new ArrayList<>(buildRetrievalWarnings(results));
        if (outcome.rerankDegraded()) {
            warnings.add("重排序模型暂不可用，本次结果按基础相关性排序。");
        }

        return KnowledgeSearchResponse.builder()
                .results(results)
                .total(results.size())
                .processSummary(buildProcessSummary(request, outcome.items().size(), results))
                .citations(buildCitationReferences(results))
                .warnings(warnings)
                .build();
    }

    /** 检索结果 + 重排序是否降级 */
    private record RetrievalOutcome(List<KnowledgeSearchResponse.SearchResultItem> items, boolean rerankDegraded) {}

    /**
     * 真混合检索：向量召回 + 关键词召回双路执行，RRF 融合排序后送重排序模型精排。
     * 精排失败时降级使用 RRF 归一化分数，并如实标记 rerankDegraded。
     */
    private RetrievalOutcome hybridSearch(String query, float[] queryVector, String knowledgeBaseId, int topK,
                                          boolean includeKnowledgeBase, boolean includeRequirementBody) {
        int candidateSize = Math.min(Math.max(topK * 3, 30), 100);
        Long kbIdLong = knowledgeBaseId != null ? Long.valueOf(knowledgeBaseId) : null;

        List<KnowledgeSearchResponse.SearchResultItem> vectorItems =
                semanticSearch(queryVector, knowledgeBaseId, candidateSize, includeKnowledgeBase, includeRequirementBody);
        List<KnowledgeSearchResponse.SearchResultItem> keywordItems =
                keywordSearch(query, kbIdLong, candidateSize, includeKnowledgeBase, includeRequirementBody);

        Map<String, Double> rrfScores = new HashMap<>();
        Map<String, KnowledgeSearchResponse.SearchResultItem> itemByKey = new LinkedHashMap<>();
        accumulateRrf(vectorItems, rrfScores, itemByKey);
        accumulateRrf(keywordItems, rrfScores, itemByKey);
        if (itemByKey.isEmpty()) {
            return new RetrievalOutcome(Collections.emptyList(), false);
        }

        List<KnowledgeSearchResponse.SearchResultItem> fused = itemByKey.keySet().stream()
                .sorted(Comparator.comparing((String key) -> rrfScores.get(key)).reversed())
                .map(itemByKey::get)
                .collect(Collectors.toList());

        // 只把 RRF 融合后的头部候选送重排序模型，控制调用开销
        int rerankLimit = Math.min(fused.size(), Math.max(knowledgeConfig.getRerankTopK(), topK));
        List<KnowledgeSearchResponse.SearchResultItem> candidates = fused.subList(0, rerankLimit);

        try {
            List<String> documents = candidates.stream()
                    .map(item -> item.getContent() == null ? "" : item.getContent())
                    .collect(Collectors.toList());
            List<Double> rerankScores = embeddingService.rerank(query, documents);
            for (int i = 0; i < candidates.size(); i++) {
                double score = i < rerankScores.size() ? rerankScores.get(i) : 0d;
                candidates.get(i).setScore(score);
            }
            candidates.sort(Comparator.comparingDouble(
                    (KnowledgeSearchResponse.SearchResultItem item) -> item.getScore() == null ? 0d : item.getScore()).reversed());
            return new RetrievalOutcome(candidates, false);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Reranker调用失败，降级使用 RRF 融合排序", e);
            double maxRrf = rrfScores.values().stream().mapToDouble(Double::doubleValue).max().orElse(1d);
            for (KnowledgeSearchResponse.SearchResultItem item : candidates) {
                String key = fusionKey(item);
                double normalized = maxRrf > 0 ? rrfScores.getOrDefault(key, 0d) / maxRrf : 0d;
                item.setScore(normalized);
            }
            return new RetrievalOutcome(candidates, true);
        }
    }

    /** 单路召回按名次累积 RRF 分数，并以融合键去重合并片段元数据 */
    private void accumulateRrf(List<KnowledgeSearchResponse.SearchResultItem> items,
                               Map<String, Double> rrfScores,
                               Map<String, KnowledgeSearchResponse.SearchResultItem> itemByKey) {
        for (int rank = 0; rank < items.size(); rank++) {
            KnowledgeSearchResponse.SearchResultItem item = items.get(rank);
            String key = fusionKey(item);
            rrfScores.merge(key, 1.0 / (RRF_K + rank + 1), Double::sum);
            KnowledgeSearchResponse.SearchResultItem existing = itemByKey.putIfAbsent(key, item);
            if (existing != null && existing.getRequirement() == null && item.getRequirement() != null) {
                existing.setRequirement(item.getRequirement());
            }
        }
    }

    /** 融合去重键：优先 chunkId，缺失时退化为 documentId + 内容指纹 */
    private String fusionKey(KnowledgeSearchResponse.SearchResultItem item) {
        if (item.getChunkId() != null) {
            return "c:" + item.getChunkId();
        }
        return "d:" + item.getDocumentId() + "#" + (item.getContent() == null ? 0 : item.getContent().hashCode());
    }

    /**
     * 从最终结果中挑选进入 LLM 上下文的片段：结果已按相关度降序，
     * 低于最低分阈值或超出字符预算的尾部片段不参与回答生成（结果列表仍完整展示）。
     */
    private List<KnowledgeSearchResponse.SearchResultItem> selectContextResults(
            List<KnowledgeSearchResponse.SearchResultItem> results) {
        if (results == null || results.isEmpty()) {
            return List.of();
        }
        double minScore = knowledgeConfig.getContextMinScore();
        int maxChars = knowledgeConfig.getContextMaxChars();
        List<KnowledgeSearchResponse.SearchResultItem> selected = new ArrayList<>();
        int usedChars = 0;
        for (KnowledgeSearchResponse.SearchResultItem item : results) {
            int length = item.getContent() == null ? 0 : item.getContent().length();
            double score = item.getScore() == null ? 0d : item.getScore();
            // 首条始终保留，保证至少有一段参考材料
            if (!selected.isEmpty() && (score < minScore || usedChars + length > maxChars)) {
                break;
            }
            selected.add(item);
            usedChars += length;
        }
        if (selected.size() < results.size()) {
            log.info("上下文裁剪：{} 条片段中 {} 条进入 LLM（预算 {} 字符，最低分 {}）",
                    results.size(), selected.size(), maxChars, minScore);
        }
        return selected;
    }

    // ==================== 向量检索 ====================

    private List<KnowledgeSearchResponse.SearchResultItem> semanticSearch(
            float[] queryVector, String knowledgeBaseId, int topK,
            boolean includeKnowledgeBase, boolean includeRequirementBody) {
        List<MilvusVectorStore.SearchResult> milvusResults =
                searchMilvusInScopes(queryVector, knowledgeBaseId, topK,
                        includeKnowledgeBase, includeRequirementBody);
        Map<Long, KnowledgeSearchResponse.RequirementReference> reqMap =
                buildDocumentRequirementMap(collectDocumentIds(milvusResults));
        Map<String, KnowledgeChunk> chunkMap = buildChunkMetadataMap(milvusResults);
        return milvusResults.stream()
                .map(sr -> toResultItem(sr, reqMap, chunkMap))
                .collect(Collectors.toList());
    }

    /**
     * 按检索范围搜索 Milvus：
     * 集合支持 source_type 标量字段时用一次组合过滤搜索完成；旧集合回退为双路搜索后内存合并。
     */
    private List<MilvusVectorStore.SearchResult> searchMilvusInScopes(
            float[] queryVector, String knowledgeBaseId, int topK,
            boolean includeKnowledgeBase, boolean includeRequirementBody) {
        if (milvusVectorStore.supportsSourceTypeFilter()) {
            return milvusVectorStore.searchByFilter(queryVector,
                    buildScopeFilter(knowledgeBaseId, includeKnowledgeBase, includeRequirementBody), topK);
        }
        return searchMilvusTwoPass(queryVector, knowledgeBaseId, topK,
                includeKnowledgeBase, includeRequirementBody);
    }

    /**
     * 组合范围过滤表达式（仅在集合具备 source_type 字段时使用）。
     */
    private String buildScopeFilter(String knowledgeBaseId, boolean includeKnowledgeBase, boolean includeRequirementBody) {
        boolean filterByKb = includeKnowledgeBase && knowledgeBaseId != null;
        if (filterByKb && includeRequirementBody) {
            return "(knowledge_base_id == \"" + knowledgeBaseId + "\") or (source_type == \"requirement_body\")";
        }
        if (filterByKb) {
            return "knowledge_base_id == \"" + knowledgeBaseId + "\"";
        }
        if (includeRequirementBody && !includeKnowledgeBase) {
            return "source_type == \"requirement_body\"";
        }
        return null;
    }

    /**
     * 旧集合回退方案：指定库搜索 + 全局搜索两路执行后按来源合法性内存合并。
     */
    private List<MilvusVectorStore.SearchResult> searchMilvusTwoPass(
            float[] queryVector, String knowledgeBaseId, int topK,
            boolean includeKnowledgeBase, boolean includeRequirementBody) {
        List<MilvusVectorStore.SearchResult> selected = includeKnowledgeBase
                ? milvusVectorStore.search(queryVector, knowledgeBaseId, Math.min(Math.max(topK * 3, 50), 300))
                : List.of();
        List<MilvusVectorStore.SearchResult> globalCandidates = includeRequirementBody
                ? milvusVectorStore.search(queryVector, null, Math.min(Math.max(topK * 5, 100), 500))
                : List.of();
        Set<Long> documentIds = collectDocumentIds(selected);
        documentIds.addAll(collectDocumentIds(globalCandidates));
        Map<Long, KnowledgeDocument> documents = documentIds.isEmpty()
                ? Collections.emptyMap()
                : knowledgeDocumentMapper.selectBatchIds(documentIds).stream()
                        .collect(Collectors.toMap(KnowledgeDocument::getId, Function.identity(), (left, right) -> left));
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

    // ==================== 关键词检索 ====================

    private List<KnowledgeSearchResponse.SearchResultItem> keywordSearch(
            String query, Long knowledgeBaseId, int topK,
            boolean includeKnowledgeBase, boolean includeRequirementBody) {
        String normalizedQuery = normalizeKeyword(query);
        if (normalizedQuery.isBlank()) {
            return Collections.emptyList();
        }

        int candidateLimit = Math.min(Math.max(topK * 5, 50), 200);
        List<String> terms = tokenizeKeyword(normalizedQuery);
        Map<Long, KeywordCandidate> candidates = new LinkedHashMap<>();

        if (includeKnowledgeBase) {
            LambdaQueryWrapper<KnowledgeChunk> chunkWrapper = new LambdaQueryWrapper<>();
            if (knowledgeBaseId != null) {
                chunkWrapper.eq(KnowledgeChunk::getKnowledgeBaseId, knowledgeBaseId);
            }
            chunkWrapper.and(wrapper -> appendContentMatches(wrapper, normalizedQuery, terms));
            chunkWrapper.last("LIMIT " + candidateLimit);
            for (KnowledgeChunk chunk : knowledgeChunkMapper.selectList(chunkWrapper)) {
                candidates.putIfAbsent(chunk.getId(), new KeywordCandidate(chunk, false));
            }
        }
        // 工单正文是独立来源，可跨知识库检索；正文引用后续按权限过滤。
        if (includeRequirementBody) {
            for (KnowledgeChunk chunk : knowledgeChunkMapper.searchRequirementBodyChunks(normalizedQuery, terms, candidateLimit)) {
                candidates.putIfAbsent(chunk.getId(), new KeywordCandidate(chunk, false));
            }
        }
        // 文件名命中 → 召回该文档全部分块（限流）
        List<Long> fileMatchedDocIds = new ArrayList<>();
        if (includeKnowledgeBase) {
            fileMatchedDocIds.addAll(findDocumentIdsByFileName(normalizedQuery, terms, knowledgeBaseId, false));
        }
        if (includeRequirementBody) {
            fileMatchedDocIds.addAll(findDocumentIdsByFileName(normalizedQuery, terms, null, true));
        }
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
                .map(candidate -> candidate.chunk().getDocumentId())
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, KnowledgeDocument> documentMap = documentIdsToMap(docIds);
        candidates.entrySet().removeIf(entry -> {
            KnowledgeDocument document = documentMap.get(entry.getValue().chunk().getDocumentId());
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
                candidates.values().stream().map(candidate -> candidate.chunk().getDocumentId())
                        .filter(Objects::nonNull).collect(Collectors.toSet()));

        return candidates.values().stream()
                .map(candidate -> toResultItem(candidate, documentMap, requirementMap, normalizedQuery, terms))
                .sorted(Comparator.comparingDouble(KnowledgeSearchResponse.SearchResultItem::getScore).reversed())
                .limit(topK)
                .collect(Collectors.toList());
    }

    /** content / section_title 的 LIKE 匹配条件 */
    private void appendContentMatches(LambdaQueryWrapper<KnowledgeChunk> wrapper, String normalizedQuery, List<String> terms) {
        wrapper.like(KnowledgeChunk::getContent, normalizedQuery)
                .or()
                .like(KnowledgeChunk::getSectionTitle, normalizedQuery);
        for (String term : terms) {
            wrapper.or().like(KnowledgeChunk::getContent, term)
                    .or()
                    .like(KnowledgeChunk::getSectionTitle, term);
        }
    }

    /** 按文件名匹配文档（普通文档或工单正文二选一），避免重复的 wrapper 构造 */
    private List<Long> findDocumentIdsByFileName(String normalizedQuery, List<String> terms,
                                                 Long knowledgeBaseId, boolean requirementBodyOnly) {
        LambdaQueryWrapper<KnowledgeDocument> wrapper = new LambdaQueryWrapper<>();
        if (requirementBodyOnly) {
            wrapper.eq(KnowledgeDocument::getSourceType, "requirement_body");
        } else {
            if (knowledgeBaseId != null) {
                wrapper.eq(KnowledgeDocument::getKnowledgeBaseId, knowledgeBaseId);
            }
        }
        wrapper.and(inner -> {
            inner.like(KnowledgeDocument::getFileName, normalizedQuery);
            for (String term : terms) {
                inner.or().like(KnowledgeDocument::getFileName, term);
            }
        });
        wrapper.last("LIMIT 50");
        return knowledgeDocumentMapper.selectList(wrapper).stream()
                .map(KnowledgeDocument::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    // ==================== 结果组装与过滤 ====================

    private KnowledgeSearchResponse.SearchResultItem toResultItem(
            MilvusVectorStore.SearchResult sr,
            Map<Long, KnowledgeSearchResponse.RequirementReference> requirementMap,
            Map<String, KnowledgeChunk> chunkMetadataMap) {
        Map<String, Object> entity = sr.getEntity();
        Long docId = parseLong(entity.get("document_id"));
        String vectorId = getString(entity.get("id"));
        KnowledgeChunk metadata = chunkMetadataMap.get(vectorId);
        return KnowledgeSearchResponse.SearchResultItem.builder()
                .chunkId(metadata != null ? metadata.getId() : null)
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
        KnowledgeChunk chunk = candidate.chunk();
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
                .collect(Collectors.toMap(KnowledgeChunk::getVectorId, Function.identity(), (left, right) -> left));
    }

    private Set<Long> collectDocumentIds(List<MilvusVectorStore.SearchResult> results) {
        return results.stream()
                .map(sr -> parseLong(sr.getEntity().get("document_id")))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private Map<Long, KnowledgeDocument> documentIdsToMap(Set<Long> docIds) {
        if (docIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return knowledgeDocumentMapper.selectBatchIds(docIds).stream()
                .collect(Collectors.toMap(KnowledgeDocument::getId, Function.identity(), (a, b) -> a));
    }

    /**
     * 文档 → 关联工单引用映射。
     * 优先使用文档上记录的 requirementId；缺失时按文件名兜底匹配带附件工单
     * （候选文件名限量 OR LIKE 查询，避免把全表附件载入内存）。
     */
    private Map<Long, KnowledgeSearchResponse.RequirementReference> buildDocumentRequirementMap(Collection<Long> docIds) {
        if (docIds == null || docIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<KnowledgeDocument> documents = knowledgeDocumentMapper.selectBatchIds(docIds);
        Map<Long, Long> docToReqId = documents.stream()
                .filter(d -> d.getRequirementId() != null)
                .collect(Collectors.toMap(KnowledgeDocument::getId, KnowledgeDocument::getRequirementId, (a, b) -> a));

        Map<Long, KnowledgeSearchResponse.RequirementReference> resultMap = new HashMap<>();
        if (!docToReqId.isEmpty()) {
            List<Requirement> reqs = requirementMapper.selectBatchIds(docToReqId.values());
            Map<Long, Requirement> reqMap = reqs.stream()
                    .collect(Collectors.toMap(Requirement::getId, r -> r, (a, b) -> a));
            for (Map.Entry<Long, Long> entry : docToReqId.entrySet()) {
                Requirement req = reqMap.get(entry.getValue());
                if (req != null) {
                    resultMap.put(entry.getKey(), toRequirementReference(req));
                }
            }
        }

        Set<String> unmatchedFileNames = documents.stream()
                .filter(document -> !resultMap.containsKey(document.getId()))
                .map(KnowledgeDocument::getFileName)
                .filter(name -> name != null && !name.isBlank())
                .collect(Collectors.toSet());
        if (unmatchedFileNames.isEmpty() || unmatchedFileNames.size() > MAX_FILENAME_FALLBACK_MATCHES) {
            return resultMap;
        }

        LambdaQueryWrapper<Requirement> wrapper = new LambdaQueryWrapper<Requirement>()
                .isNotNull(Requirement::getAttachments)
                .and(w -> {
                    for (String name : unmatchedFileNames) {
                        w.or().like(Requirement::getAttachments, name);
                    }
                })
                .last("LIMIT 200");
        for (Requirement req : requirementMapper.selectList(wrapper)) {
            for (KnowledgeDocument document : documents) {
                String fileName = document.getFileName();
                if (!resultMap.containsKey(document.getId()) && fileName != null
                        && hasAttachmentNamed(req, fileName)) {
                    resultMap.put(document.getId(), toRequirementReference(req));
                }
            }
        }
        return resultMap;
    }

    private boolean hasAttachmentNamed(Requirement requirement, String fileName) {
        if (requirement.getAttachments() == null) {
            return false;
        }
        return requirement.getAttachments().stream()
                .anyMatch(attachment -> attachment.getName() != null
                        && attachment.getName().equalsIgnoreCase(fileName));
    }

    /**
     * 按权限过滤工单正文类结果；一次批量加载文档与工单，避免逐条查询。
     */
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
        Map<Long, KnowledgeDocument> documentMap = documentIdsToMap(documentIds);
        Set<Long> requirementIds = documentMap.values().stream()
                .map(KnowledgeDocument::getRequirementId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, Requirement> requirementMap = requirementIds.isEmpty()
                ? Collections.emptyMap()
                : requirementMapper.selectBatchIds(requirementIds).stream()
                        .collect(Collectors.toMap(Requirement::getId, Function.identity(), (left, right) -> left));
        Long userId = requesterId != null ? requesterId : com.demand.system.module.auth.security.SecurityUtils.getCurrentUserId();
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

    private List<String> buildRetrievalWarnings(List<KnowledgeSearchResponse.SearchResultItem> results) {
        if (results == null || results.isEmpty() || imageUnderstandingService.enabled()) {
            return List.of();
        }
        Set<Long> documentIds = results.stream()
                .map(KnowledgeSearchResponse.SearchResultItem::getDocumentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, KnowledgeDocument> bodyDocuments = documentIdsToMap(documentIds).values().stream()
                .filter(document -> "requirement_body".equals(document.getSourceType()))
                .collect(Collectors.toMap(KnowledgeDocument::getId, Function.identity(), (left, right) -> left));
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

    /** 解析 TopK，优先级：模型配置 > 全局配置 */
    private int resolveTopK() {
        var modelConfig = embeddingService.getDefaultModelConfig();
        if (modelConfig != null) {
            return modelConfig.searchTopK();
        }
        return knowledgeConfig.getSearchTopK();
    }

    // ==================== 思维链 / 摘要 / 引用 ====================

    private List<KnowledgeSearchResponse.ThinkingStep> buildThinkingSteps(
            String query, String mode, KnowledgeSearchResponse response) {
        List<KnowledgeSearchResponse.ThinkingStep> steps = new ArrayList<>();
        steps.add(new KnowledgeSearchResponse.ThinkingStep(
                "query_parse", "问题解析", buildQueryParseDetail(query, null)));

        int resultCount = response.getResults().size();
        int uniqueDocs = countUniqueDocuments(response.getResults());
        steps.add(new KnowledgeSearchResponse.ThinkingStep(
                "retrieve", "文档检索",
                String.format("在知识库中检索到 %d 条相关片段，来自 %d 份文档", resultCount, uniqueDocs),
                resultCount > 0 ? Math.min(1.0, resultCount / 10.0) : 0.0));

        if ("hybrid".equals(mode) && !hasRerankDegraded(response)) {
            steps.add(new KnowledgeSearchResponse.ThinkingStep(
                    "rerank", "智能排序",
                    String.format("使用重排序模型优化结果顺序，优先呈现最相关的 %d 条片段", Math.min(resultCount, 5))));
        }

        String synthesizeDetail = response.getAnswer() != null
                ? String.format("已基于 %d 条检索片段生成回答（%d 字）", resultCount, response.getAnswer().length())
                : String.format("未使用 AI 模型，共检索到 %d 条相关片段", resultCount);
        steps.add(new KnowledgeSearchResponse.ThinkingStep(
                "synthesize", response.getAnswer() != null ? "生成回答" : "检索摘要", synthesizeDetail));

        return steps;
    }

    private String buildQueryParseDetail(String query, QueryRewriteResult rewrite) {
        List<String> keywords = rewrite != null && rewrite.rewritten() && !rewrite.keywords().isEmpty()
                ? rewrite.keywords()
                : Arrays.stream(query.split("[\\s,，。；;:：/\\\\|]+"))
                        .map(String::trim)
                        .filter(t -> t.length() >= 2)
                        .limit(5)
                        .collect(Collectors.toList());
        if (keywords.isEmpty()) {
            return String.format("已解析问题：%s", shortenText(query, 30));
        }
        String rewrittenHint = rewrite != null && rewrite.rewritten()
                && !rewrite.safeStandaloneQuery().equals(query)
                ? "（改写为「" + shortenText(rewrite.safeStandaloneQuery(), 30) + "）" : "";
        return String.format("已解析问题，关键词：%s%s", String.join("、", keywords), rewrittenHint);
    }

    private boolean hasRerankDegraded(KnowledgeSearchResponse response) {
        return response.getWarnings() != null && response.getWarnings().stream()
                .anyMatch(warning -> warning.contains("重排序模型暂不可用"));
    }

    private int countUniqueDocuments(List<KnowledgeSearchResponse.SearchResultItem> results) {
        return (int) results.stream()
                .map(KnowledgeSearchResponse.SearchResultItem::getDocumentId)
                .filter(Objects::nonNull)
                .distinct()
                .count();
    }

    private KnowledgeSearchRequest copyWithQuery(KnowledgeSearchRequest request, String query) {
        KnowledgeSearchRequest copy = new KnowledgeSearchRequest();
        copy.setQuery(query != null && !query.isBlank() ? query : request.getQuery());
        copy.setKnowledgeBaseId(request.getKnowledgeBaseId());
        copy.setMode(request.getMode());
        copy.setTopK(request.getTopK());
        copy.setLlmModelId(request.getLlmModelId());
        copy.setSearchScopes(request.getSearchScopes());
        copy.setHistory(request.getHistory());
        copy.setRequesterId(request.getRequesterId());
        return copy;
    }

    private String buildProcessSummary(KnowledgeSearchRequest request, int candidateCount, List<KnowledgeSearchResponse.SearchResultItem> results) {
        String mode = normalizeMode(request.getMode());
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

    /**
     * 构建角标引用列表：按 documentId 分组，按相关度降序，分配连续角标序号 [1] [2] ...
     * 文档元数据一次批量加载。
     */
    private List<KnowledgeSearchResponse.CitationReference> buildCitationReferences(
            List<KnowledgeSearchResponse.SearchResultItem> results) {
        if (results == null || results.isEmpty()) {
            return List.of();
        }

        Map<Long, List<KnowledgeSearchResponse.SearchResultItem>> byDoc = results.stream()
                .filter(r -> r.getDocumentId() != null)
                .collect(Collectors.groupingBy(KnowledgeSearchResponse.SearchResultItem::getDocumentId,
                        LinkedHashMap::new, Collectors.toList()));
        Map<Long, KnowledgeDocument> documents = documentIdsToMap(byDoc.keySet());

        List<KnowledgeSearchResponse.CitationReference> refs = new ArrayList<>();
        for (Map.Entry<Long, List<KnowledgeSearchResponse.SearchResultItem>> entry : byDoc.entrySet()) {
            Long docId = entry.getKey();
            List<KnowledgeSearchResponse.SearchResultItem> items = entry.getValue();

            KnowledgeSearchResponse.SearchResultItem first = items.get(0);
            double maxScore = items.stream()
                    .mapToDouble(item -> item.getScore() == null ? 0.0 : item.getScore())
                    .max()
                    .orElse(0.0);
            KnowledgeDocument document = documents.get(docId);
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

        refs.sort((a, b) -> Double.compare(b.getMaxScore(), a.getMaxScore()));
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

    // ==================== 工具方法 ====================

    private String normalizeMode(String mode) {
        return mode == null ? "hybrid" : mode;
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
        String content = lower(candidate.chunk().getContent());
        String sectionTitle = lower(candidate.chunk().getSectionTitle());
        String fileName = lower(document != null ? document.getFileName() : null);
        String lowerQuery = lower(query);

        double score = 0.1d;
        if (contains(content, lowerQuery)) score += 0.55d;
        if (contains(sectionTitle, lowerQuery)) score += 0.2d;
        if (contains(fileName, lowerQuery)) score += 0.3d;
        if (candidate.fileNameMatched()) score += 0.15d;

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

    private String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private String shortenText(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "…";
    }

    private record SearchScopeDecision(boolean includeKnowledgeBase, boolean includeRequirementBody) {}

    private record KeywordCandidate(KnowledgeChunk chunk, boolean fileNameMatched) {}
}
