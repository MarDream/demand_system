package com.demand.system.module.knowledge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "knowledge")
public class KnowledgeConfig {

    // ==== Document chunking ====
    private int chunkSize = 512;
    private int chunkOverlap = 128;
    /** "char" | "token" - chunking strategy */
    private String chunkingMode = "char";
    /** token mode: max tokens per chunk */
    private int maxTokens = 512;
    /** token mode: overlap tokens between chunks */
    private int overlapTokens = 128;

    // ==== Search ====
    private int searchTopK = 20;
    /** 进入 LLM 上下文的片段最低分数（rerank/语义相关性量纲），低分尾部不参与回答生成 */
    private double contextMinScore = 0.15;
    /** 注入 LLM 的参考资料字符预算，超出时丢弃低分尾部片段 */
    private int contextMaxChars = 24000;

    // ==== Multi-hop event expansion ====
    /** max multi-hop expansion depth */
    private int maxHops = 1;
    /** max candidate events after expansion */
    private int maxEvents = 100;
    /** top-K entities to recall from query */
    private int entityTopK = 20;
    /** minimum vector similarity threshold (0.0 ~ 1.0) */
    private double similarityThreshold = 0.4;

    // ==== Rerank ====
    /** candidates sent to reranker */
    private int rerankTopK = 50;
    /** rerank call timeout in milliseconds */
    private int rerankTimeoutMs = 15000;
    /** enable LLM rerank (standard mode) */
    private boolean enableLlmRerank = false;

    // ==== Embedding ====
    private int embeddingBatchSize = 16;
    private long embeddingDelayMs = 100;

    // ==== Milvus batch insert ====
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
