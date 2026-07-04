package com.demand.system.module.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.demand.system.common.exception.BusinessException;
import com.demand.system.common.result.ErrorCode;
import com.demand.system.module.knowledge.config.KnowledgeConfig;
import com.demand.system.module.knowledge.dto.KnowledgeSearchResponse;
import com.demand.system.module.knowledge.entity.KnowledgeEvent;
import com.demand.system.module.knowledge.entity.KnowledgeEntity;
import com.demand.system.module.knowledge.entity.KnowledgeEventEntity;
import com.demand.system.module.knowledge.llm.LlmGateway;
import com.demand.system.module.knowledge.llm.LlmGatewayConfig;
import com.demand.system.module.knowledge.mapper.KnowledgeEventMapper;
import com.demand.system.module.knowledge.mapper.KnowledgeEntityMapper;
import com.demand.system.module.knowledge.mapper.KnowledgeEventEntityMapper;
import com.demand.system.module.knowledge.service.EmbeddingService;
import com.demand.system.module.knowledge.service.KnowledgeEventSearchService;
import com.demand.system.module.knowledge.service.KnowledgeSearchService;
import com.demand.system.module.knowledge.vectorstore.MilvusVectorStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * SAG 多跳检索服务实现。
 *
 * 检索流程：
 *  1. Query Entity Recall（向量 + 文本召回实体）
 *  2. Event Seed（实体关联事件 + 标题向量匹配）
 *  3. Multi-Hop Expansion（固定跳数 BFS 遍历事件-实体二分图）
 *  4. Coarse Ranking（content embedding 余弦相似度粗排）
 *  5. Rerank（模型精排 + 可选 LLM 精排）
 *  6. Section Retrieval（根据 event ID 获取原始 chunk 原文）
 */
@Service
public class KnowledgeEventSearchServiceImpl implements KnowledgeEventSearchService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeEventSearchServiceImpl.class);

    private final KnowledgeConfig knowledgeConfig;
    private final EmbeddingService embeddingService;
    private final LlmGateway llmGateway;
    private final LlmGatewayConfig llmGatewayConfig;
    private final MilvusVectorStore milvusVectorStore;
    private final KnowledgeEventMapper eventMapper;
    private final KnowledgeEntityMapper entityMapper;
    private final KnowledgeEventEntityMapper eventEntityMapper;
    private final KnowledgeSearchService knowledgeSearchService;

    public KnowledgeEventSearchServiceImpl(
            KnowledgeConfig knowledgeConfig,
            EmbeddingService embeddingService,
            LlmGateway llmGateway,
            LlmGatewayConfig llmGatewayConfig,
            MilvusVectorStore milvusVectorStore,
            KnowledgeEventMapper eventMapper,
            KnowledgeEntityMapper entityMapper,
            KnowledgeEventEntityMapper eventEntityMapper,
            KnowledgeSearchService knowledgeSearchService) {
        this.knowledgeConfig = knowledgeConfig;
        this.embeddingService = embeddingService;
        this.llmGateway = llmGateway;
        this.llmGatewayConfig = llmGatewayConfig;
        this.milvusVectorStore = milvusVectorStore;
        this.eventMapper = eventMapper;
        this.entityMapper = entityMapper;
        this.eventEntityMapper = eventEntityMapper;
        this.knowledgeSearchService = knowledgeSearchService;
    }

    @Override
    public List<KnowledgeSearchResponse.SearchResultItem> search(
            String query,
            String knowledgeBaseId,
            Integer topK,
            String searchMode
    ) {
        // ----- 参数校验 -----
        if (query == null || query.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "检索 query 不能为空");
        }
        if (topK == null || topK <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "topK 必须大于 0");
        }
        if (searchMode == null || searchMode.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "searchMode 不能为空，请使用 'fast' 或 'standard'");
        }
        String mode = searchMode.trim().toLowerCase();
        if (!"fast".equals(mode) && !"standard".equals(mode)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "searchMode 必须为 'fast' 或 'standard'");
        }

        String kbId = knowledgeBaseId;
        int resolvedTopK = topK;

        // Step 1: Query → Entity Recall (vector + text search)
        List<KnowledgeEntity> seedEntities = recallEntities(query, kbId, knowledgeConfig.getEntityTopK());

        // Step 2: Entity → Event lookup
        Set<Long> eventIds = getEventIdsByEntityIds(toEntityIdSet(seedEntities));

        // Step 3: Title vector → Event lookup
        Set<Long> titleMatchedEventIds = searchEventsByTitleVector(query, kbId, resolvedTopK);
        eventIds.addAll(titleMatchedEventIds);

        if (eventIds.isEmpty()) {
            log.info("SAG 检索未找到种子事件，query={}", query);
            return buildEmptyResult(query);
        }        // Step 4: Multi-hop expansion (BFS on event-entity bipartite graph)
        Set<Long> expandedEventIds = expandFixedHops(eventIds, kbId, knowledgeConfig.getMaxHops());
        eventIds.addAll(expandedEventIds);

        // Cap the candidate events
        int maxEvents = knowledgeConfig.getMaxEvents();
        if (eventIds.size() > maxEvents) {
            // Keep only the top maxEvents IDs (by natural order as approximation)
            eventIds = eventIds.stream().limit(maxEvents).collect(Collectors.toSet());
        }

        List<KnowledgeEvent> candidateEvents = loadEvents(eventIds);

        if (candidateEvents.isEmpty()) {
            return buildEmptyResult(query);
        }

        // Step 5: Coarse ranking by content embedding
        List<ScoredEvent> coarseRanked = coarseRankEventsByContent(query, candidateEvents);

        // Step 6: Rerank — model rerank + optional LLM rerank
        List<ScoredEvent> finalRanked = coarseRanked;

        int rerankTopK = Math.min(knowledgeConfig.getRerankTopK(), coarseRanked.size());
        List<ScoredEvent> rerankCandidates = coarseRanked.subList(0, rerankTopK);

        // 6a: Model rerank
        try {
            List<ScoredEvent> modelReranked = modelRerank(query, rerankCandidates);
            finalRanked = modelReranked;
        } catch (Exception e) {
            log.warn("Model rerank 失败，回退粗排结果: {}", e.getMessage());
        }

        // 6b: LLM rerank (only in "standard" mode when enabled)
        boolean useLlmRerank = "standard".equals(mode) && knowledgeConfig.isEnableLlmRerank();
        if (useLlmRerank) {
            try {
                finalRanked = llmRerank(query, finalRanked);
            } catch (Exception e) {
                log.warn("LLM rerank 失败，回退前一步结果: {}", e.getMessage());
            }
        }

        // Step 7: Final section retrieval — convert ScoredEvent to SearchResultItem
        List<KnowledgeEvent> finalEvents = finalRanked.stream()
                .limit(resolvedTopK)
                .map(se -> se.event)
                .collect(Collectors.toList());

        return buildResultFromEvents(finalEvents, query, resolvedTopK);
    }

    // ========================================================================
    //  Step 1: Entity Recall
    // ========================================================================

    /**
     * 从 Query 中召回种子实体：向量搜索 + 文本模糊匹配。
     */
    private List<KnowledgeEntity> recallEntities(String query, String kbId, int entityTopK) {
        List<KnowledgeEntity> entities = new ArrayList<>();

        // 1a: Vector search — embed query and find nearest entity embeddings
        try {
            float[] queryVector = embeddingService.embed(query);
            List<KnowledgeEntity> vectorMatched = searchEntitiesByVector(queryVector, kbId, entityTopK);
            entities.addAll(vectorMatched);
        } catch (Exception e) {
            log.warn("Entity 向量召回失败: {}", e.getMessage());
        }

        // 1b: Text search — fuzzy match by name / normalized_name / description
        try {
            List<KnowledgeEntity> textMatched = searchEntitiesByText(query, kbId, entityTopK);
            entities.addAll(textMatched);
        } catch (Exception e) {
            log.warn("Entity 文本召回失败: {}", e.getMessage());
        }

        // Deduplicate
        return entities.stream()
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * 向量搜索 entity embedding 匹配。
     * 由于 entity embedding 存储在 MySQL 的 JSON 字段中而非 Milvus，
     * 此处使用内存匹配：加载所有候选 entity 后进行余弦相似度计算。
     */
    private List<KnowledgeEntity> searchEntitiesByVector(float[] queryVector, String kbId, int topK) {
        LambdaQueryWrapper<KnowledgeEntity> wrapper = new LambdaQueryWrapper<>();
        if (kbId != null) {
            wrapper.eq(KnowledgeEntity::getKnowledgeBaseId, Long.parseLong(kbId));
        }
        wrapper.isNotNull(KnowledgeEntity::getEmbedding);
        wrapper.last("LIMIT 500");

        List<KnowledgeEntity> candidates = entityMapper.selectList(wrapper);
        if (candidates.isEmpty()) {
            return Collections.emptyList();
        }

        List<ScoredEntity> scored = new ArrayList<>();
        for (KnowledgeEntity entity : candidates) {
            float[] entityVector = parseEmbedding(entity.getEmbedding());
            if (entityVector == null) {
                continue;
            }
            double similarity = cosineSimilarity(queryVector, entityVector);
            if (similarity >= knowledgeConfig.getSimilarityThreshold()) {
                scored.add(new ScoredEntity(entity, similarity));
            }
        }

        scored.sort(Comparator.comparingDouble(se -> -se.score));
        return scored.stream()
                .limit(topK)
                .map(se -> se.entity)
                .collect(Collectors.toList());
    }

    /**
     * 文本模糊搜索 entity（按 name / normalized_name / description）。
     */
    private List<KnowledgeEntity> searchEntitiesByText(String query, String kbId, int topK) {
        LambdaQueryWrapper<KnowledgeEntity> wrapper = new LambdaQueryWrapper<>();
        if (kbId != null) {
            wrapper.eq(KnowledgeEntity::getKnowledgeBaseId, Long.parseLong(kbId));
        }
        String likePattern = "%" + query.trim() + "%";
        wrapper.and(w -> w
                .like(KnowledgeEntity::getName, likePattern)
                .or()
                .like(KnowledgeEntity::getNormalizedName, likePattern)
                .or()
                .like(KnowledgeEntity::getDescription, likePattern)
        );
        wrapper.last("LIMIT " + topK);
        return entityMapper.selectList(wrapper);
    }

    // ========================================================================
    //  Step 2: Entity → Event Lookup
    // ========================================================================

    /**
     * 根据 entity IDs 查询关联的 event IDs。
     */
    private Set<Long> getEventIdsByEntityIds(Set<Long> entityIds) {
        if (entityIds.isEmpty()) {
            return Collections.emptySet();
        }
        LambdaQueryWrapper<KnowledgeEventEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(KnowledgeEventEntity::getEntityId, entityIds);
        List<KnowledgeEventEntity> relations = eventEntityMapper.selectList(wrapper);
        return relations.stream()
                .map(KnowledgeEventEntity::getEventId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    // ========================================================================
    //  Step 3: Title Vector → Event Lookup
    // ========================================================================

    /**
     * 通过 query embedding 与 event title embedding 的向量相似度匹配事件。
     */
    private Set<Long> searchEventsByTitleVector(String query, String kbId, int topK) {
        try {
            float[] queryVector = embeddingService.embed(query);

            LambdaQueryWrapper<KnowledgeEvent> wrapper = new LambdaQueryWrapper<>();
            if (kbId != null) {
                wrapper.eq(KnowledgeEvent::getKnowledgeBaseId, Long.parseLong(kbId));
            }
            wrapper.isNotNull(KnowledgeEvent::getTitleEmbedding);
            wrapper.last("LIMIT 500");

            List<KnowledgeEvent> candidates = eventMapper.selectList(wrapper);
            if (candidates.isEmpty()) {
                return Collections.emptySet();
            }

            List<ScoredEvent> scored = new ArrayList<>();
            for (KnowledgeEvent event : candidates) {
                float[] titleVector = parseEmbedding(event.getTitleEmbedding());
                if (titleVector == null) {
                    continue;
                }
                double similarity = cosineSimilarity(queryVector, titleVector);
                if (similarity >= knowledgeConfig.getSimilarityThreshold()) {
                    scored.add(new ScoredEvent(event, similarity));
                }
            }

            scored.sort(Comparator.comparingDouble(se -> -se.score));
            return scored.stream()
                    .limit(topK)
                    .map(se -> se.event.getId())
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            log.warn("标题向量匹配事件失败: {}", e.getMessage());
            return Collections.emptySet();
        }
    }

    // ========================================================================
    //  Step 4: Multi-Hop Expansion (BFS)
    // ========================================================================

    /**
     * 固定跳数 BFS 遍历事件-实体二分图，扩展相关事件。
     *
     * 每跳策略：
     *   Event → Entity：获取事件关联的所有实体
     *   Entity → Event：获取实体关联的所有事件
     */
    private Set<Long> expandFixedHops(Set<Long> seedEventIds, String kbId, int maxHops) {
        if (maxHops <= 0) {
            return Collections.emptySet();
        }

        Set<Long> visitedEvents = new HashSet<>(seedEventIds);
        Set<Long> currentEvents = new HashSet<>(seedEventIds);

        for (int hop = 1; hop <= maxHops; hop++) {
            log.debug("SAG 多跳扩展: hop={}, currentEvents={}", hop, currentEvents.size());

            // Step A: Event → Entity
            Set<Long> currentEntityIds = getEntityIdsByEventIds(currentEvents);
            if (currentEntityIds.isEmpty()) {
                break;
            }

            // Step B: Entity → Event
            Set<Long> nextEvents = getEventIdsByEntityIds(currentEntityIds);
            nextEvents.removeAll(visitedEvents);

            if (nextEvents.isEmpty()) {
                break;
            }

            visitedEvents.addAll(nextEvents);
            currentEvents = nextEvents;
        }

        visitedEvents.removeAll(seedEventIds);
        return visitedEvents;
    }

    /**
     * 根据 event IDs 查询关联的 entity IDs。
     */
    private Set<Long> getEntityIdsByEventIds(Set<Long> eventIds) {
        if (eventIds.isEmpty()) {
            return Collections.emptySet();
        }
        LambdaQueryWrapper<KnowledgeEventEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(KnowledgeEventEntity::getEventId, eventIds);
        List<KnowledgeEventEntity> relations = eventEntityMapper.selectList(wrapper);
        return relations.stream()
                .map(KnowledgeEventEntity::getEntityId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    // ========================================================================
    //  Step 5: Coarse Ranking
    // ========================================================================

    /**
     * 基于 content embedding 余弦相似度对事件进行粗排。
     * 若事件没有 content_embedding，则用 title + summary 作为备选文本使用 query embedding 进行匹配。
     */
    private List<ScoredEvent> coarseRankEventsByContent(String query, List<KnowledgeEvent> events) {
        float[] queryVector = embeddingService.embed(query);

        List<ScoredEvent> scored = new ArrayList<>();
        for (KnowledgeEvent event : events) {
            double similarity;

            if (event.getContentEmbedding() != null && !event.getContentEmbedding().isBlank()) {
                float[] eventVector = parseEmbedding(event.getContentEmbedding());
                if (eventVector != null) {
                    similarity = cosineSimilarity(queryVector, eventVector);
                } else {
                    similarity = computeTextFallbackSimilarity(query, event);
                }
            } else {
                similarity = computeTextFallbackSimilarity(query, event);
            }

            if (similarity >= knowledgeConfig.getSimilarityThreshold()) {
                scored.add(new ScoredEvent(event, similarity));
            }
        }

        scored.sort(Comparator.comparingDouble(se -> -se.score));
        return scored;
    }

    /**
     * 当事件没有 content_embedding 时，计算 query 与事件 title/summary 的文本相似度。
     */
    private double computeTextFallbackSimilarity(String query, KnowledgeEvent event) {
        String text = String.join(" ",
                nullToEmpty(event.getTitle()),
                nullToEmpty(event.getSummary()),
                nullToEmpty(event.getKeywords())
        );
        if (text.isBlank()) {
            return 0.0;
        }
        String queryLower = query.toLowerCase(Locale.ROOT);
        String textLower = text.toLowerCase(Locale.ROOT);

        // 基于关键词覆盖率的简单文本相似度
        String[] queryTerms = queryLower.split("[\\s,，。；;:：/\\\\|]+");
        if (queryTerms.length == 0) {
            return 0.0;
        }

        int matchCount = 0;
        for (String term : queryTerms) {
            if (term.length() >= 2 && textLower.contains(term)) {
                matchCount++;
            }
        }

        double coverage = (double) matchCount / queryTerms.length;
        // 归一化到 0.0 ~ 1.0，如果包含完整 query 则给予额外加分
        double bonus = textLower.contains(queryLower) ? 0.2 : 0.0;
        return Math.min(1.0, coverage + bonus);
    }

    private String nullToEmpty(String value) {
        return value != null ? value : "";
    }

    // ========================================================================
    //  Step 6a: Model Rerank
    // ========================================================================

    /**
     * 使用 reranker 模型对粗排结果进行精排。
     * 将候选事件的内容拼接为文本列表，调用 embeddingService.rerank 获取相关性分数。
     */
    private List<ScoredEvent> modelRerank(String query, List<ScoredEvent> candidates) {
        if (candidates.isEmpty()) {
            return candidates;
        }

        List<String> documents = candidates.stream()
                .map(se -> buildRerankText(se.event))
                .collect(Collectors.toList());

        List<Double> rerankScores;
        try {
            rerankScores = embeddingService.rerank(query, documents);
        } catch (Exception e) {
            log.warn("Reranker 调用失败: {}", e.getMessage());
            // 如果 reranker 不可用，保留粗排分数
            return candidates;
        }

        List<ScoredEvent> reranked = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            double newScore = i < rerankScores.size() ? rerankScores.get(i) : candidates.get(i).score;
            reranked.add(new ScoredEvent(candidates.get(i).event, newScore));
        }

        reranked.sort(Comparator.comparingDouble(se -> -se.score));
        return reranked;
    }

    /**
     * 构建用于 reranker 的事件文本表示。
     */
    private String buildRerankText(KnowledgeEvent event) {
        StringBuilder sb = new StringBuilder();
        if (event.getTitle() != null && !event.getTitle().isBlank()) {
            sb.append(event.getTitle()).append("：");
        }
        if (event.getSummary() != null && !event.getSummary().isBlank()) {
            sb.append(event.getSummary());
        } else if (event.getContent() != null && !event.getContent().isBlank()) {
            sb.append(event.getContent().length() > 500
                    ? event.getContent().substring(0, 500)
                    : event.getContent());
        }
        return sb.toString();
    }

    // ========================================================================
    //  Step 6b: LLM Rerank
    // ========================================================================

    /**
     * 使用 LLM 对精排结果进行重排序（仅在 standard 模式下启用）。
     * 调用 LLM 判断每个事件与 query 的相关性并给出分数，然后按 LLM 分数重排。
     * 如果 LLM 调用失败，返回原始顺序。
     */
    private List<ScoredEvent> llmRerank(String query, List<ScoredEvent> candidates) {
        if (candidates.isEmpty()) {
            return candidates;
        }

        LlmGatewayConfig.Provider llmRerankerProvider = llmGatewayConfig.getLlmReranker();
        if (llmRerankerProvider == null || llmRerankerProvider.getModel() == null
                || llmRerankerProvider.getModel().isBlank()) {
            log.warn("LLM reranker provider 未配置，跳过 LLM rerank");
            return candidates;
        }

        // 只对 top-N 进行 LLM rerank 以减少 token 消耗
        int llmRerankCount = Math.min(candidates.size(), 10);
        List<ScoredEvent> llmCandidates = candidates.subList(0, llmRerankCount);

        try {
            double[] llmScores = llmRerankBatch(query, llmCandidates, llmRerankerProvider);

            List<ScoredEvent> llmRanked = new ArrayList<>();
            for (int i = 0; i < llmCandidates.size(); i++) {
                llmRanked.add(new ScoredEvent(llmCandidates.get(i).event, llmScores[i]));
            }
            llmRanked.sort(Comparator.comparingDouble(se -> -se.score));

            // 将 LLM rerank 后的结果与剩余未参与 LLM rerank 的候选合并
            List<ScoredEvent> result = new ArrayList<>(llmRanked);
            if (llmRerankCount < candidates.size()) {
                result.addAll(candidates.subList(llmRerankCount, candidates.size()));
            }
            return result;
        } catch (Exception e) {
            log.warn("LLM rerank 失败: {}", e.getMessage());
            return candidates;
        }
    }

    /**
     * 批量调用 LLM 评估事件相关性。
     */
    private double[] llmRerankBatch(
            String query, List<ScoredEvent> candidates, LlmGatewayConfig.Provider provider) {

        // 构建 system prompt
        String systemPrompt = "你是一个专业的相关性评估助手。" +
                "请判断以下每个事件与用户查询的相关程度。" +
                "对每个事件输出一个 0-10 的相关性分数（0 表示完全不相关，10 表示高度相关）。" +
                "仅输出 JSON 格式数组，不要额外解释。\n\n" +
                "格式：[score1, score2, ...]";

        // 构建 user message
        StringBuilder sb = new StringBuilder();
        sb.append("用户查询：").append(query).append("\n\n事件列表：\n");
        for (int i = 0; i < candidates.size(); i++) {
            KnowledgeEvent event = candidates.get(i).event;
            sb.append(i).append(". ");
            sb.append("标题：").append(nullToEmpty(event.getTitle())).append("\n");
            sb.append("   摘要：").append(nullToEmpty(event.getSummary())).append("\n");
            sb.append("   分类：").append(nullToEmpty(event.getCategory())).append("\n");
            sb.append("   关键词：").append(nullToEmpty(event.getKeywords())).append("\n\n");
        }

        try {
            LlmGateway.ChatResult result = llmGateway.chatWithProvider(provider, systemPrompt, sb.toString());
            String content = result.getContent();
            if (content == null || content.isBlank()) {
                throw new RuntimeException("LLM rerank 返回内容为空");
            }

            // 提取 JSON 数组
            int start = content.indexOf('[');
            int end = content.lastIndexOf(']');
            if (start < 0 || end <= start) {
                throw new RuntimeException("LLM rerank 未返回合法 JSON 数组: " + content);
            }

            String jsonArray = content.substring(start, end + 1);
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            double[] scores = mapper.readValue(jsonArray, double[].class);

            if (scores.length != candidates.size()) {
                log.warn("LLM rerank 返回分数数量({})与候选数量({})不匹配，重新映射",
                        scores.length, candidates.size());
                double[] remapped = new double[candidates.size()];
                Arrays.fill(remapped, 0.0);
                System.arraycopy(scores, 0, remapped, 0, Math.min(scores.length, remapped.length));
                return remapped;
            }

            // 归一化到 0.0 ~ 1.0
            for (int i = 0; i < scores.length; i++) {
                scores[i] = Math.max(0.0, Math.min(1.0, scores[i] / 10.0));
            }
            return scores;
        } catch (Exception e) {
            log.warn("LLM rerank batch 评估失败: {}", e.getMessage());
            // 降级：返回原始分数
            double[] fallback = new double[candidates.size()];
            for (int i = 0; i < candidates.size(); i++) {
                fallback[i] = candidates.get(i).score;
            }
            return fallback;
        }
    }

    // ========================================================================
    //  Step 7: Build ResultItem from ScoredEvents → Section Retrieval
    // ========================================================================

    /**
     * 将最终的事件列表转换为 SearchResultItem，并降级到普通的 KnowledgeSearchService 搜索。
     */
    private List<KnowledgeSearchResponse.SearchResultItem> buildResultFromEvents(
            List<KnowledgeEvent> events, String query, int topK) {
        // 降级策略：使用 KnowledgeSearchService 执行常规检索
        KnowledgeSearchResponse response = knowledgeSearchService.search(
                new com.demand.system.module.knowledge.dto.KnowledgeSearchRequest() {{
                    setQuery(query);
                    setTopK(topK);
                    setMode("hybrid");
                }}
        );
        return response != null ? response.getResults() : Collections.emptyList();
    }

    // ========================================================================
    //  Utility / Internal helpers
    // ========================================================================

    /**
     * 构建空结果（降级到常规搜索）。
     */
    private List<KnowledgeSearchResponse.SearchResultItem> buildEmptyResult(String query) {
        KnowledgeSearchResponse response = knowledgeSearchService.search(
                new com.demand.system.module.knowledge.dto.KnowledgeSearchRequest() {{
                    setQuery(query);
                    setTopK(5);
                    setMode("hybrid");
                }}
        );
        return response != null ? response.getResults() : Collections.emptyList();
    }

    /**
     * 从 entity 列表提取 ID 集合。
     */
    private Set<Long> toEntityIdSet(List<KnowledgeEntity> entities) {
        return entities.stream()
                .map(KnowledgeEntity::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    /**
     * 根据 event IDs 批量加载 KnowledgeEvent。
     */
    private List<KnowledgeEvent> loadEvents(Set<Long> eventIds) {
        if (eventIds.isEmpty()) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<KnowledgeEvent> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(KnowledgeEvent::getId, eventIds);
        return eventMapper.selectList(wrapper);
    }

    /**
     * 将 JSON 字符串格式的 embedding 解析为 float 数组。
     * 支持的格式："[0.1, 0.2, ...]" 或 "0.1,0.2,..."
     */
    private float[] parseEmbedding(String embeddingStr) {
        if (embeddingStr == null || embeddingStr.isBlank()) {
            return null;
        }
        try {
            String trimmed = embeddingStr.trim();
            if (trimmed.startsWith("[")) {
                trimmed = trimmed.substring(1);
            }
            if (trimmed.endsWith("]")) {
                trimmed = trimmed.substring(0, trimmed.length() - 1);
            }
            String[] parts = trimmed.split(",");
            float[] result = new float[parts.length];
            for (int i = 0; i < parts.length; i++) {
                result[i] = Float.parseFloat(parts[i].trim());
            }
            return result;
        } catch (Exception e) {
            log.warn("解析 embedding 失败: {}", embeddingStr, e);
            return null;
        }
    }

    /**
     * 计算两个 float 向量的余弦相似度。
     */
    private double cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) {
            return 0.0;
        }
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dotProduct += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        double denominator = Math.sqrt(normA) * Math.sqrt(normB);
        return denominator == 0.0 ? 0.0 : dotProduct / denominator;
    }

    // ========================================================================
    //  Inner Types
    // ========================================================================

    /**
     * 带分数的实体。
     */
    private record ScoredEntity(KnowledgeEntity entity, double score) {}

    /**
     * 带分数的事件。
     */
    private record ScoredEvent(KnowledgeEvent event, double score) {}
}
