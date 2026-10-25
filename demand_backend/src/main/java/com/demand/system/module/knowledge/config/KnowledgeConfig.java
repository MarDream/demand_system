package com.demand.system.module.knowledge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "knowledge")
public class KnowledgeConfig {

    // --- 文档分片 ---
    private int chunkSize = 512;
    private int chunkOverlap = 128;
    /** 分片策略："char" 或 "token"。 */
    private String chunkingMode = "char";
    /** token 模式：单分片的最大 token 数。 */
    private int maxTokens = 512;
    /** token 模式：相邻分片的重叠 token 数。 */
    private int overlapTokens = 128;

    // --- 检索 ---
    private int searchTopK = 20;
    /** 进入 LLM 上下文的片段最低分数（rerank/语义相关性量纲），低分尾部不参与回答生成 */
    private double contextMinScore = 0.15;
    /**
     * 相对相关度下限：片段分数低于「首条分数 × 该系数」即视为与问题弱相关，不进入 LLM 上下文与引用来源。
     * 与 {@link #contextMinScore} 取较大值生效，用于自适应地砍掉高分结果后面的弱相关长尾
     * （reranker 打分量纲随模型浮动，绝对阈值难以通吃，故再叠加相对阈值）。
     * 取值偏保守：实测命中文档的分数会低到首条的 0.5 倍左右，取 0.5 会切进相关区间，
     * 0.4 只砍掉明显无关的长尾。
     */
    private double contextRelativeFloor = 0.4;
    /** 注入 LLM 的参考资料字符预算，超出时丢弃低分尾部片段 */
    private int contextMaxChars = 24000;

    // --- 多跳事件扩展 ---
    /** 多跳扩展的最大深度。 */
    private int maxHops = 1;
    /** 扩展后候选事件的最大数量。 */
    private int maxEvents = 100;
    /** 从 query 召回的实体 top-K。 */
    private int entityTopK = 20;
    /** 向量相似度的最低阈值（0.0 ~ 1.0）。 */
    private double similarityThreshold = 0.4;

    // --- 重排序 ---
    /** 送入 reranker 的候选数量。 */
    private int rerankTopK = 50;
    /** rerank 调用超时（毫秒）。 */
    private int rerankTimeoutMs = 15000;
    /** 是否启用 LLM 精排（standard 模式）。 */
    private boolean enableLlmRerank = false;

    // --- Embedding ---
    private int embeddingBatchSize = 16;
    private long embeddingDelayMs = 100;

    // --- Milvus 批量写入 ---
    private int milvusInsertBatchSize = 200;

    public int getChunkSize() {
        return chunkSize;
    }

    public void setChunkSize(int chunkSize) {
        this.chunkSize = chunkSize;
    }

    public int getChunkOverlap() {
        return chunkOverlap;
    }

    public void setChunkOverlap(int chunkOverlap) {
        this.chunkOverlap = chunkOverlap;
    }

    public String getChunkingMode() {
        return chunkingMode;
    }

    public void setChunkingMode(String chunkingMode) {
        this.chunkingMode = chunkingMode;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    public int getOverlapTokens() {
        return overlapTokens;
    }

    public void setOverlapTokens(int overlapTokens) {
        this.overlapTokens = overlapTokens;
    }

    public int getSearchTopK() {
        return searchTopK;
    }

    public void setSearchTopK(int searchTopK) {
        this.searchTopK = searchTopK;
    }

    public double getContextMinScore() {
        return contextMinScore;
    }

    public void setContextMinScore(double contextMinScore) {
        this.contextMinScore = contextMinScore;
    }

    public double getContextRelativeFloor() {
        return contextRelativeFloor;
    }

    public void setContextRelativeFloor(double contextRelativeFloor) {
        this.contextRelativeFloor = contextRelativeFloor;
    }

    public int getContextMaxChars() {
        return contextMaxChars;
    }

    public void setContextMaxChars(int contextMaxChars) {
        this.contextMaxChars = contextMaxChars;
    }

    public int getMaxHops() {
        return maxHops;
    }

    public void setMaxHops(int maxHops) {
        this.maxHops = maxHops;
    }

    public int getMaxEvents() {
        return maxEvents;
    }

    public void setMaxEvents(int maxEvents) {
        this.maxEvents = maxEvents;
    }

    public int getEntityTopK() {
        return entityTopK;
    }

    public void setEntityTopK(int entityTopK) {
        this.entityTopK = entityTopK;
    }

    public double getSimilarityThreshold() {
        return similarityThreshold;
    }

    public void setSimilarityThreshold(double similarityThreshold) {
        this.similarityThreshold = similarityThreshold;
    }

    public int getRerankTopK() {
        return rerankTopK;
    }

    public void setRerankTopK(int rerankTopK) {
        this.rerankTopK = rerankTopK;
    }

    public int getRerankTimeoutMs() {
        return rerankTimeoutMs;
    }

    public void setRerankTimeoutMs(int rerankTimeoutMs) {
        this.rerankTimeoutMs = rerankTimeoutMs;
    }

    public boolean isEnableLlmRerank() {
        return enableLlmRerank;
    }

    public void setEnableLlmRerank(boolean enableLlmRerank) {
        this.enableLlmRerank = enableLlmRerank;
    }

    public int getEmbeddingBatchSize() {
        return embeddingBatchSize;
    }

    public void setEmbeddingBatchSize(int embeddingBatchSize) {
        this.embeddingBatchSize = embeddingBatchSize;
    }

    public long getEmbeddingDelayMs() {
        return embeddingDelayMs;
    }

    public void setEmbeddingDelayMs(long embeddingDelayMs) {
        this.embeddingDelayMs = embeddingDelayMs;
    }

    public int getMilvusInsertBatchSize() {
        return milvusInsertBatchSize;
    }

    public void setMilvusInsertBatchSize(int milvusInsertBatchSize) {
        this.milvusInsertBatchSize = milvusInsertBatchSize;
    }
}
