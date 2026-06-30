package com.demand.system.module.knowledge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "knowledge")
public class KnowledgeConfig {

    private int chunkSize = 512;
    private int chunkOverlap = 128;
    private int searchTopK = 20;
    private int embeddingBatchSize = 16;
    private long embeddingDelayMs = 100;
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

    public int getSearchTopK() {
        return searchTopK;
    }

    public void setSearchTopK(int searchTopK) {
        this.searchTopK = searchTopK;
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
