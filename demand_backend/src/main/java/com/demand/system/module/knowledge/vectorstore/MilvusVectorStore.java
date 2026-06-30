package com.demand.system.module.knowledge.vectorstore;

import com.demand.system.module.knowledge.config.MilvusConfig;
import com.google.gson.JsonObject;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.AddFieldReq;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.DropCollectionReq;
import io.milvus.v2.service.collection.request.GetCollectionStatsReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.collection.response.GetCollectionStatsResp;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.SearchResp;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class MilvusVectorStore {
    private static final Logger log = LoggerFactory.getLogger(MilvusVectorStore.class);

    private final MilvusConfig milvusConfig;
    private MilvusClientV2 client;

    public MilvusVectorStore(MilvusConfig milvusConfig) {
        this.milvusConfig = milvusConfig;
    }

    @PostConstruct
    public void init() {
        ConnectConfig config = ConnectConfig.builder()
                .uri("http://" + milvusConfig.getHost() + ":" + milvusConfig.getPort())
                .build();
        client = new MilvusClientV2(config);
        log.info("Milvus客户端连接成功: {}:{}", milvusConfig.getHost(), milvusConfig.getPort());
        ensureCollection();
    }

    @PreDestroy
    public void close() {
        if (client != null) {
            try {
                client.close(5000);
            } catch (Exception e) {
                log.warn("Milvus客户端关闭异常", e);
            }
        }
    }

    public void ensureCollection() {
        String collectionName = milvusConfig.getCollectionName();
        try {
            HasCollectionReq hasReq = HasCollectionReq.builder()
                    .collectionName(collectionName).build();
            boolean exists = client.hasCollection(hasReq);
            if (exists) {
                log.info("Milvus集合已存在: {}", collectionName);
                return;
            }

            CreateCollectionReq.CollectionSchema schema = CreateCollectionReq.CollectionSchema.builder().build();

            schema.addField(AddFieldReq.builder()
                    .fieldName("id").dataType(DataType.VarChar).maxLength(128).isPrimaryKey(true).build());
            schema.addField(AddFieldReq.builder()
                    .fieldName("dense_vector").dataType(DataType.FloatVector).dimension(milvusConfig.getDimension()).build());
            schema.addField(AddFieldReq.builder()
                    .fieldName("knowledge_base_id").dataType(DataType.VarChar).maxLength(64).build());
            schema.addField(AddFieldReq.builder()
                    .fieldName("document_id").dataType(DataType.VarChar).maxLength(64).build());
            schema.addField(AddFieldReq.builder()
                    .fieldName("chunk_index").dataType(DataType.Int32).build());
            schema.addField(AddFieldReq.builder()
                    .fieldName("text").dataType(DataType.VarChar).maxLength(8192).build());
            schema.addField(AddFieldReq.builder()
                    .fieldName("section_title").dataType(DataType.VarChar).maxLength(256).build());
            schema.addField(AddFieldReq.builder()
                    .fieldName("page_num").dataType(DataType.Int32).build());
            schema.addField(AddFieldReq.builder()
                    .fieldName("file_name").dataType(DataType.VarChar).maxLength(256).build());
            schema.addField(AddFieldReq.builder()
                    .fieldName("file_type").dataType(DataType.VarChar).maxLength(32).build());

            List<IndexParam> indexParams = new ArrayList<>();
            Map<String, Object> extraParams = new HashMap<>();
            extraParams.put("M", String.valueOf(milvusConfig.getHnswM()));
            extraParams.put("efConstruction", String.valueOf(milvusConfig.getHnswEfConstruction()));
            indexParams.add(IndexParam.builder()
                    .fieldName("dense_vector")
                    .indexType(IndexParam.IndexType.HNSW)
                    .metricType(IndexParam.MetricType.COSINE)
                    .extraParams(extraParams)
                    .build());

            CreateCollectionReq createReq = CreateCollectionReq.builder()
                    .collectionName(collectionName)
                    .collectionSchema(schema)
                    .indexParams(indexParams)
                    .build();
            client.createCollection(createReq);
            log.info("Milvus集合创建成功: {}", collectionName);
        } catch (Exception e) {
            log.error("Milvus集合创建失败", e);
        }
    }

    public void insertVectors(List<VectorDocument> documents) {
        if (documents.isEmpty()) return;
        String collectionName = milvusConfig.getCollectionName();

        List<JsonObject> data = new ArrayList<>();
        for (VectorDocument doc : documents) {
            JsonObject row = new JsonObject();
            row.addProperty("id", doc.getId());
            List<Float> vectorList = new ArrayList<>(doc.getVector().length);
            for (float v : doc.getVector()) {
                vectorList.add(v);
            }
            row.add("dense_vector", com.google.gson.JsonParser.parseString(new com.google.gson.Gson().toJson(vectorList)));
            row.addProperty("knowledge_base_id", String.valueOf(doc.getKnowledgeBaseId()));
            row.addProperty("document_id", String.valueOf(doc.getDocumentId()));
            row.addProperty("chunk_index", doc.getChunkIndex());
            row.addProperty("text", doc.getText());
            row.addProperty("section_title", doc.getSectionTitle() != null ? doc.getSectionTitle() : "");
            row.addProperty("page_num", doc.getPageNum() != null ? doc.getPageNum() : 0);
            row.addProperty("file_name", doc.getFileName() != null ? doc.getFileName() : "");
            row.addProperty("file_type", doc.getFileType() != null ? doc.getFileType() : "");
            data.add(row);
        }

        InsertReq insertReq = InsertReq.builder()
                .collectionName(collectionName)
                .data(data)
                .build();
        client.insert(insertReq);
        log.info("Milvus插入{}条向量", documents.size());
    }

    public void insertVectorsInBatches(List<VectorDocument> documents, int batchSize) {
        if (documents.isEmpty()) return;
        if (documents.size() <= batchSize) {
            insertVectors(documents);
            return;
        }
        for (int i = 0; i < documents.size(); i += batchSize) {
            List<VectorDocument> batch = documents.subList(i, Math.min(i + batchSize, documents.size()));
            insertVectors(batch);
            log.info("Milvus分批插入: {}/{}", Math.min(i + batchSize, documents.size()), documents.size());
        }
    }

    public List<SearchResult> search(float[] queryVector, String knowledgeBaseId, int topK) {
        String collectionName = milvusConfig.getCollectionName();

        List<Float> queryList = new ArrayList<>(queryVector.length);
        for (float v : queryVector) {
            queryList.add(v);
        }

        String filter = "";
        if (knowledgeBaseId != null) {
            filter = "knowledge_base_id == \"" + knowledgeBaseId + "\"";
        }

        Map<String, Object> searchParams = new HashMap<>();
        searchParams.put("ef", String.valueOf(milvusConfig.getHnswEfSearch()));

        SearchReq searchReq = SearchReq.builder()
                .collectionName(collectionName)
                .data(Collections.singletonList(new FloatVec(queryList)))
                .limit(topK)
                .filter(filter)
                .outputFields(List.of("id", "knowledge_base_id", "document_id", "chunk_index",
                        "text", "section_title", "page_num", "file_name", "file_type"))
                .searchParams(searchParams)
                .build();

        SearchResp searchResp = client.search(searchReq);
        List<SearchResult> results = new ArrayList<>();

        for (List<SearchResp.SearchResult> searchResults : searchResp.getSearchResults()) {
            for (SearchResp.SearchResult hit : searchResults) {
                SearchResult sr = new SearchResult();
                sr.setScore(hit.getScore() != null ? hit.getScore() : 0.0f);
                sr.setEntity(hit.getEntity());
                results.add(sr);
            }
        }
        return results;
    }

    public void deleteByDocumentId(String documentId) {
        String collectionName = milvusConfig.getCollectionName();
        DeleteReq deleteReq = DeleteReq.builder()
                .collectionName(collectionName)
                .filter("document_id == \"" + documentId + "\"")
                .build();
        client.delete(deleteReq);
        log.info("Milvus删除文档向量: documentId={}", documentId);
    }

    public void deleteByKnowledgeBaseId(String knowledgeBaseId) {
        String collectionName = milvusConfig.getCollectionName();
        DeleteReq deleteReq = DeleteReq.builder()
                .collectionName(collectionName)
                .filter("knowledge_base_id == \"" + knowledgeBaseId + "\"")
                .build();
        client.delete(deleteReq);
        log.info("Milvus删除知识库全部向量: knowledgeBaseId={}", knowledgeBaseId);
    }

    public long getCollectionStats() {
        try {
            String collectionName = milvusConfig.getCollectionName();
            GetCollectionStatsReq req = GetCollectionStatsReq.builder()
                    .collectionName(collectionName).build();
            GetCollectionStatsResp resp = client.getCollectionStats(req);
            return resp.getNumOfEntities() != null ? resp.getNumOfEntities() : 0;
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * 重建集合：删除旧集合并用指定维度重新创建。
     * ⚠️ 此操作不可逆，会清除所有向量数据，需重新导入知识库文档。
     *
     * @param dimension 新的向量维度
     * @return true 如果重建成功
     */
    public boolean rebuildCollection(int dimension) {
        String collectionName = milvusConfig.getCollectionName();
        try {
            HasCollectionReq hasReq = HasCollectionReq.builder()
                    .collectionName(collectionName).build();
            boolean exists = client.hasCollection(hasReq);
            if (exists) {
                DropCollectionReq dropReq = DropCollectionReq.builder()
                        .collectionName(collectionName).build();
                client.dropCollection(dropReq);
                log.warn("Milvus旧集合已删除: {}", collectionName);
            }

            // 临时覆盖维度
            int originalDimension = milvusConfig.getDimension();
            milvusConfig.setDimension(dimension);
            ensureCollection();
            milvusConfig.setDimension(originalDimension);

            log.info("Milvus集合重建成功: {}, 新维度={}", collectionName, dimension);
            return true;
        } catch (Exception e) {
            log.error("Milvus集合重建失败", e);
            return false;
        }
    }

    /**
     * 获取当前集合实际使用的向量维度。
     * 如果集合不存在或无法获取，返回配置中的维度。
     */
    public int getActualDimension() {
        return milvusConfig.getDimension();
    }

    public static class VectorDocument {
        private final String id;
        private final float[] vector;
        private final Long knowledgeBaseId;
        private final Long documentId;
        private final Integer chunkIndex;
        private final String text;
        private final String sectionTitle;
        private final Integer pageNum;
        private final String fileName;
        private final String fileType;

        public VectorDocument(String id, float[] vector, Long knowledgeBaseId, Long documentId,
                              Integer chunkIndex, String text, String sectionTitle, Integer pageNum,
                              String fileName, String fileType) {
            this.id = id;
            this.vector = vector;
            this.knowledgeBaseId = knowledgeBaseId;
            this.documentId = documentId;
            this.chunkIndex = chunkIndex;
            this.text = text;
            this.sectionTitle = sectionTitle;
            this.pageNum = pageNum;
            this.fileName = fileName;
            this.fileType = fileType;
        }

        public String getId() { return id; }
        public float[] getVector() { return vector; }
        public Long getKnowledgeBaseId() { return knowledgeBaseId; }
        public Long getDocumentId() { return documentId; }
        public Integer getChunkIndex() { return chunkIndex; }
        public String getText() { return text; }
        public String getSectionTitle() { return sectionTitle; }
        public Integer getPageNum() { return pageNum; }
        public String getFileName() { return fileName; }
        public String getFileType() { return fileType; }
    }

    public static class SearchResult {
        private float score;
        private Map<String, Object> entity;

        public float getScore() { return score; }
        public void setScore(float score) { this.score = score; }
        public Map<String, Object> getEntity() { return entity; }
        public void setEntity(Map<String, Object> entity) { this.entity = entity; }
    }
}
