package com.demand.system.module.knowledge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "milvus")
public class MilvusConfig {

    private String host = "localhost";
    private int port = 19530;
    private String collectionName = "knowledge_chunks";
    private String indexType = "HNSW";
    private String metricType = "COSINE";
    private int hnswM = 16;
    private int hnswEfConstruction = 256;
    private int hnswEfSearch = 128;
    private int dimension = 2048;
}
