package com.demand.system.module.knowledge.service;

import java.util.List;

/**
 * 三级事件重排序服务。
 * <p>
 * Level 1: 粗排（余弦相似度）—— 零成本，基于向量检索已有的相似度分数直接降序截断。
 * Level 2: 模型精排（Reranker API）—— 调用专用的 rerank 端点，对候选文档进行语义级重打分。
 * Level 3: LLM 精排（LLM 筛选）—— 仅在 standard 模式下启用，由 LLM 从候选事件中选出最相关的 N 个。
 * <p>
 * 各层级均具备降级能力：Level 3 失败回退到 Level 2 结果，Level 2 失败回退到 Level 1 结果。
 */
public interface EventRerankerService {

    /**
     * 对候选事件执行三级排序，返回最终精选的事件 ID 列表（按相关性降序）。
     *
     * @param query           用户原始查询
     * @param candidateEvents 候选事件列表（需包含粗排分数 {@link RankedEvent#getCoarseScore()}）
     * @param knowledgeBaseId 知识库 ID（留作扩展，当前未使用）
     * @param searchMode      搜索模式："fast" 或 "standard"。仅 standard 模式启用 Level 3 LLM 精排
     * @param topK            最终返回的事件数量上限
     * @return 排序后的事件 ID 列表，按相关性从高到低排列
     */
    List<Long> rerank(String query, List<RankedEvent> candidateEvents, String knowledgeBaseId, String searchMode, int topK);

    /**
     * 单个排序候选事件。
     * <p>
     * 包含事件的基本信息以及粗排阶段的分数，供后续层级使用。
     */
    class RankedEvent {
        private Long eventId;
        private String title;
        private String summary;
        private String content;
        private double coarseScore;
        private String[] keywords;

        public RankedEvent() {
        }

        /**
         * 构造一个候选事件。
         *
         * @param eventId    事件 ID
         * @param title      事件标题
         * @param summary    事件摘要
         * @param content    事件详细内容
         * @param coarseScore 粗排分数（如余弦相似度）
         */
        public RankedEvent(Long eventId, String title, String summary, String content, double coarseScore) {
            this.eventId = eventId;
            this.title = title;
            this.summary = summary;
            this.content = content;
            this.coarseScore = coarseScore;
        }

        public Long getEventId() {
            return eventId;
        }

        public void setEventId(Long eventId) {
            this.eventId = eventId;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getSummary() {
            return summary;
        }

        public void setSummary(String summary) {
            this.summary = summary;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public double getCoarseScore() {
            return coarseScore;
        }

        public void setCoarseScore(double coarseScore) {
            this.coarseScore = coarseScore;
        }

        public String[] getKeywords() {
            return keywords;
        }

        public void setKeywords(String[] keywords) {
            this.keywords = keywords;
        }
    }
}
