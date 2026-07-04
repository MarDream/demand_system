package com.demand.system.module.knowledge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "milvus")
public class MilvusConfig {

    // ==== Connection ====
    private String host = "localhost";
    private int port = 19530;

    // ==== Collection ====
    private String collectionName = "knowledge_chunks";
    /** event vector collection name */
    private String eventsCollectionName = "knowledge_events";
    /** entity vector collection name */
    private String entitiesCollectionName = "knowledge_entities";

    // ==== Index ====
    private String indexType = "HNSW";
    private String metricType = "COSINE";
    private int dimension = 2048;

    // ==== HNSW ====
    private int hnswM = 16;
    private int hnswEfConstruction = 256;
    /** default efSearch for normal search */
    private int hnswEfSearchDefault = 128;
    /** high-recall efSearch for expansion phase */
    private int hnswEfSearchHigh = 256;

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getCollectionName() {
        return collectionName;
    }

    public void setCollectionName(String collectionName) {
        this.collectionName = collectionName;
    }

    public String getEventsCollectionName() {
        return eventsCollectionName;
    }

    public void setEventsCollectionName(String eventsCollectionName) {
        this.eventsCollectionName = eventsCollectionName;
    }

    public String getEntitiesCollectionName() {
        return entitiesCollectionName;
    }

    public void setEntitiesCollectionName(String entitiesCollectionName) {
        this.entitiesCollectionName = entitiesCollectionName;
    }

    public String getIndexType() {
        return indexType;
    }

    public void setIndexType(String indexType) {
        this.indexType = indexType;
    }

    public String getMetricType() {
        return metricType;
    }

    public void setMetricType(String metricType) {
        this.metricType = metricType;
    }

    public int getHnswM() {
        return hnswM;
    }

    public void setHnswM(int hnswM) {
        this.hnswM = hnswM;
    }

    public int getHnswEfConstruction() {
        return hnswEfConstruction;
    }

    public void setHnswEfConstruction(int hnswEfConstruction) {
        this.hnswEfConstruction = hnswEfConstruction;
    }

    public int getHnswEfSearchDefault() {
        return hnswEfSearchDefault;
    }

    public void setHnswEfSearchDefault(int hnswEfSearchDefault) {
        this.hnswEfSearchDefault = hnswEfSearchDefault;
    }

    public int getHnswEfSearchHigh() {
        return hnswEfSearchHigh;
    }

    public void setHnswEfSearchHigh(int hnswEfSearchHigh) {
        this.hnswEfSearchHigh = hnswEfSearchHigh;
    }

    public int getDimension() {
        return dimension;
    }

    public void setDimension(int dimension) {
        this.dimension = dimension;
    }
}
