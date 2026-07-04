package com.demand.system.module.knowledge.service;

import com.demand.system.module.knowledge.dto.KnowledgeSearchResponse;
import com.demand.system.module.knowledge.entity.KnowledgeEvent;
import com.demand.system.module.knowledge.entity.KnowledgeEntity;

import java.util.List;
import java.util.Map;

/**
 * SAG 多跳检索服务接口。
 *
 * 检索流程：
 *  1. Query → Entity Recall（向量 + 文本召回实体）
 *  2. Entity → Event Lookup（通过 getEventIdsByEntityIds 获取关联事件）
 *  3. Title Vector → Event Lookup（searchEventsByTitleVector 标题向量匹配事件）
 *  4. Multi-Hop Expansion（expandFixedHops 固定跳数 BFS 遍历事件-实体二分图）
 *  5. Coarse Ranking（coarseRankEventsByContent content embedding 余弦相似度粗排）
 *  6. Three-Tier Rerank（modelRerank 模型精排 + llmRerank LLM 精排（可选））
 *  7. Final Section Retrieval（根据 event ID 获取原始 chunk 原文）
 */
public interface KnowledgeEventSearchService {

    /**
     * 执行 SAG 多跳检索，返回检索结果列表。
     *
     * @param query         用户检索 Query（不可为空或空白）
     * @param knowledgeBaseId 可选知识库 ID，为 null 时跨所有知识库检索
     * @param topK          返回结果条数上限（必须 > 0）
     * @param searchMode    检索模式：{@code "fast"}（跳过 LLM 精排）或 {@code "standard"}（含 LLM 精排）
     * @return 检索结果列表
     * @throws com.demand.system.common.exception.BusinessException 当参数不合法时抛出
     */
    List<KnowledgeSearchResponse.SearchResultItem> search(
            String query,
            String knowledgeBaseId,
            Integer topK,
            String searchMode
    );
}
