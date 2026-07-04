package com.demand.system.module.knowledge.service.impl;

import com.demand.system.module.knowledge.config.KnowledgeConfig;
import com.demand.system.module.knowledge.llm.LlmGateway;
import com.demand.system.module.knowledge.llm.LlmGatewayConfig;
import com.demand.system.module.knowledge.service.EmbeddingService;
import com.demand.system.module.knowledge.service.EventRerankerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 三级事件重排序服务实现。
 * <p>
 * Level 1 — 粗排（Coarse Rank）：基于余弦相似度直接降序截断候选，零 API 成本。
 * Level 2 — 模型精排（Model Rerank）：调用 rerank 端点对粗排结果重新打分。
 * Level 3 — LLM 精排（LLM Rerank，仅 standard 模式）：由 LLM 从候选事件中选出最相关的 N 个，精度最高但最慢。
 * <p>
 * 降级链：Level 3 异常 → Level 2 结果；Level 2 异常 → Level 1 结果。
 */
@Service
public class EventRerankerServiceImpl implements EventRerankerService {

    private static final Logger log = LoggerFactory.getLogger(EventRerankerServiceImpl.class);

    private final KnowledgeConfig knowledgeConfig;
    private final EmbeddingService embeddingService;
    private final LlmGateway llmGateway;
    private final LlmGatewayConfig llmGatewayConfig;
    private final ObjectMapper objectMapper;

    public EventRerankerServiceImpl(
            KnowledgeConfig knowledgeConfig,
            EmbeddingService embeddingService,
            LlmGateway llmGateway,
            LlmGatewayConfig llmGatewayConfig,
            ObjectMapper objectMapper) {
        this.knowledgeConfig = knowledgeConfig;
        this.embeddingService = embeddingService;
        this.llmGateway = llmGateway;
        this.llmGatewayConfig = llmGatewayConfig;
        this.objectMapper = objectMapper;
    }

    /**
     * 对候选事件执行三级排序，返回最终精选的事件 ID 列表。
     *
     * @param query           用户原始查询
     * @param candidateEvents 候选事件列表（需包含粗排分数）
     * @param knowledgeBaseId 知识库 ID（当前版本未使用，保留扩展性）
     * @param searchMode      搜索模式：{@code "fast"} 或 {@code "standard"}
     * @param topK            最终返回的事件数量上限
     * @return 按相关性降序排列的事件 ID 列表
     */
    @Override
    public List<Long> rerank(String query, List<RankedEvent> candidateEvents,
                            String knowledgeBaseId, String searchMode, int topK) {
        if (candidateEvents == null || candidateEvents.isEmpty()) {
            log.info("候选事件列表为空，直接返回空列表");
            return List.of();
        }

        log.info("开始三级重排序: 候选事件数={}, 知识库ID={}, 搜索模式={}, topK={}",
                candidateEvents.size(), knowledgeBaseId, searchMode, topK);

        // ---- Level 1: 粗排 ----
        List<RankedEvent> coarseRanked = candidateEvents.stream()
                .sorted(Comparator.comparingDouble(RankedEvent::getCoarseScore).reversed())
                .limit(knowledgeConfig.getRerankTopK())
                .collect(Collectors.toList());

        log.info("Level 1 粗排完成: {} -> {} 候选（阈值={}）",
                candidateEvents.size(), coarseRanked.size(), knowledgeConfig.getRerankTopK());

        // ---- Level 2: 模型精排 ----
        List<RankedEvent> reranked;
        try {
            reranked = modelRerank(query, coarseRanked);
            log.info("Level 2 模型精排完成: {} -> {} 候选", coarseRanked.size(), reranked.size());
        } catch (Exception e) {
            log.warn("Level 2 模型精排失败，降级到粗排结果: {}", e.getMessage());
            reranked = coarseRanked;
        }

        // ---- Level 3: LLM 精排（仅 standard 模式启用）----
        if ("standard".equalsIgnoreCase(searchMode) && knowledgeConfig.isEnableLlmRerank()) {
            try {
                List<Long> llmRankedIds = llmRerank(query, reranked, topK);
                log.info("Level 3 LLM 精排完成: {} -> {} 候选", reranked.size(), llmRankedIds.size());
                return llmRankedIds;
            } catch (Exception e) {
                log.warn("Level 3 LLM 精排失败，降级到模型精排结果: {}", e.getMessage());
            }
        }

        // 返回 Level 2 结果（若 Level 2 也失败，coarseRanked 即为 Level 1 结果）
        return reranked.stream()
                .map(RankedEvent::getEventId)
                .limit(topK)
                .collect(Collectors.toList());
    }

    /**
     * Level 2: 模型精排。
     * <p>
     * 将候选事件拼接为文档字符串，调用 {@link EmbeddingService#rerank(String, List)} 对每条文档打分，
     * 再与粗排分数合并后重新排序。
     *
     * @param query    用户查询
     * @param candidates 粗排后的候选事件列表
     * @return 按模型精排分数重新排序的候选事件列表
     */
    private List<RankedEvent> modelRerank(String query, List<RankedEvent> candidates) {
        if (candidates.isEmpty()) {
            return candidates;
        }

        List<String> documents = candidates.stream()
                .map(e -> String.format("标题：%s\n摘要：%s\n内容：%s",
                        e.getTitle() != null ? e.getTitle() : "",
                        e.getSummary() != null ? e.getSummary() : "",
                        truncateText(e.getContent(), 800)))
                .collect(Collectors.toList());

        List<Double> scores = embeddingService.rerank(query, documents);

        // 将 rerank 分数与原粗排分数取最大值，确保低质量精排分数不劣于粗排结果
        List<RankedEvent> result = new ArrayList<>(candidates.size());
        for (int i = 0; i < candidates.size(); i++) {
            RankedEvent event = candidates.get(i);
            double rerankScore = (i < scores.size()) ? scores.get(i) : event.getCoarseScore();
            event.setCoarseScore(Math.max(event.getCoarseScore(), rerankScore));
            result.add(event);
        }

        return result.stream()
                .sorted(Comparator.comparingDouble(RankedEvent::getCoarseScore).reversed())
                .collect(Collectors.toList());
    }

    /**
     * Level 3: LLM 精排（LLM Rerank）。
     * <p>
     * 将候选事件列表发给 LLM，要求其从候选中选出最相关的 N 个事件，
     * 返回对应的 eventId 列表。仅当 {@code searchMode == "standard"} 且配置启用时调用。
     * <p>
     * LLM 结果解析采用启发式 JSON 索引提取，解析失败则回退到原排序。
     *
     * @param query    用户查询
     * @param candidates 模型精排后的候选事件列表
     * @param topK     期望返回的最终数量
     * @return LLM 认为最相关的 eventId 列表
     */
    private List<Long> llmRerank(String query, List<RankedEvent> candidates, int topK) {
        LlmGatewayConfig.Provider provider = llmGatewayConfig.getLlmReranker();
        if (provider.getBaseUrl() == null || provider.getBaseUrl().isBlank()) {
            log.warn("LLM Reranker 未配置（baseUrl 为空），跳过 Level 3");
            return candidates.stream()
                    .map(RankedEvent::getEventId)
                    .limit(topK)
                    .collect(Collectors.toList());
        }

        // 取前 20 个候选发给 LLM，减少 prompt 长度和延迟
        List<RankedEvent> topCandidates = candidates.stream()
                .limit(Math.min(20, candidates.size()))
                .collect(Collectors.toList());

        StringBuilder eventList = new StringBuilder();
        for (int i = 0; i < topCandidates.size(); i++) {
            RankedEvent e = topCandidates.get(i);
            eventList.append(String.format("%d. %s [摘要: %s]%n",
                    i + 1,
                    e.getTitle() != null ? e.getTitle() : "无标题",
                    e.getSummary() != null ? e.getSummary() : "无摘要"));
        }

        String systemPrompt = "你是一个专业的知识检索排序助手。请从以下候选事件中，选择最有助于回答用户问题的 N 个事件。"
                + "只从列表中选择，不允许自行生成事件。";
        String userMessage = String.format(
                "用户问题：%s%n%n候选事件列表：%n%s%n%n请从以上列表中选择最相关的 %d 个事件序号，"
                        + "以 JSON 整数数组格式返回，例如 [1, 3, 5] 表示第 1、3、5 个事件最相关。",
                query, eventList, Math.min(topK, topCandidates.size()));

        try {
            LlmGateway.ChatResult result = llmGateway.chatWithProvider(provider, systemPrompt, userMessage);
            String content = result.getContent();

            // 启发式解析：从 LLM 返回文本中提取第一个 [...] 包裹的整数数组
            int startIdx = content.indexOf('[');
            int endIdx = content.lastIndexOf(']');
            if (startIdx >= 0 && endIdx > startIdx) {
                String json = content.substring(startIdx, endIdx + 1);
                List<Integer> indices = objectMapper.readValue(
                        json,
                        objectMapper.getTypeFactory()
                                .constructCollectionType(List.class, Integer.class));

                return indices.stream()
                        .filter(idx -> idx >= 1 && idx <= topCandidates.size())
                        .map(idx -> topCandidates.get(idx - 1).getEventId())
                        .collect(Collectors.toList());
            }
        } catch (Exception e) {
            log.warn("Level 3 LLM 精排结果解析失败，回退到模型精排顺序: {}", e.getMessage());
        }

        return candidates.stream()
                .map(RankedEvent::getEventId)
                .limit(topK)
                .collect(Collectors.toList());
    }

    /**
     * 截断文本至最大长度，超出部分追加省略号。
     *
     * @param text    待截断文本
     * @param maxLen  最大保留字符数
     * @return 截断后的文本，若原文本未超限则原样返回
     */
    private String truncateText(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }
}
