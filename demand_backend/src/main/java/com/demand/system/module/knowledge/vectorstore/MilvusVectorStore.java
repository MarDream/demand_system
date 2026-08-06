package com.demand.system.module.knowledge.vectorstore;

import com.demand.system.module.knowledge.config.MilvusConfig;
import com.google.gson.JsonObject;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.AddFieldReq;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.DescribeCollectionReq;
import io.milvus.v2.service.collection.request.DropCollectionReq;
import io.milvus.v2.service.collection.request.GetCollectionStatsReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.collection.response.DescribeCollectionResp;
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
    private volatile int actualDimension = -1;

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
                int dimension = describeDimension(collectionName);
                if (dimension > 0) {
                    actualDimension = dimension;
                    milvusConfig.setDimension(dimension);
                }
                log.info("Milvus集合已存在: {}, dimension={}", collectionName,
                        dimension > 0 ? dimension : milvusConfig.getDimension());
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
            actualDimension = milvusConfig.getDimension();
            log.info("Milvus集合创建成功: {}, dimension={}", collectionName, actualDimension);
        } catch (Exception e) {
            log.error("Milvus集合创建失败", e);
        }
    }

    public void insertVectors(List<VectorDocument> documents) {
        if (documents.isEmpty()) return;
        String collectionName = milvusConfig.getCollectionName();
        int vectorDimension = validateVectorDimensions(documents);
        ensureDimension(vectorDimension);

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
        int collectionDimension = getActualDimension();
        if (queryVector == null || queryVector.length != collectionDimension) {
            int queryDimension = queryVector == null ? 0 : queryVector.length;
            throw new IllegalArgumentException("查询向量维度(" + queryDimension
                    + ")与Milvus集合维度(" + collectionDimension + ")不一致");
        }

        List<Float> queryList = new ArrayList<>(queryVector.length);
        for (float v : queryVector) {
            queryList.add(v);
        }

        String filter = "";
        if (knowledgeBaseId != null) {
            filter = "knowledge_base_id == \"" + knowledgeBaseId + "\"";
        }

        Map<String, Object> searchParams = new HashMap<>();
        searchParams.put("ef", String.valueOf(milvusConfig.getHnswEfSearchDefault()));

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
    public synchronized boolean rebuildCollection(int dimension) {
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

            milvusConfig.setDimension(dimension);
            actualDimension = -1;
            ensureCollection();
            int createdDimension = describeDimension(collectionName);
            if (createdDimension != dimension) {
                throw new IllegalStateException("Milvus集合创建后的维度校验失败: expected="
                        + dimension + ", actual=" + createdDimension);
            }
            actualDimension = createdDimension;

            log.info("Milvus集合重建成功: {}, 新维度={}", collectionName, dimension);
            return true;
        } catch (Exception e) {
            log.error("Milvus集合重建失败", e);
            return false;
        }
    }

    /**
     * 按 embedding 接口实际返回的向量维度校准 Milvus 集合。
     * 当模型未配置 dimension 或模型切换时，以真实向量长度为准自动重建集合。
     */
    public synchronized DimensionSyncResult ensureDimension(int requiredDimension) {
        if (requiredDimension <= 0) {
            throw new IllegalArgumentException("向量维度必须大于0");
        }

        int currentDimension = getActualDimension();
        if (currentDimension == requiredDimension) {
            return DimensionSyncResult.unchanged(currentDimension);
        }

        long previousEntityCount = Math.max(0, getCollectionStats());
        log.warn("检测到Embedding实际维度与Milvus集合不一致，自动重建集合: collection={}, oldDimension={}, "
                        + "newDimension={}, previousEntityCount={}",
                milvusConfig.getCollectionName(), currentDimension, requiredDimension, previousEntityCount);

        if (!rebuildCollection(requiredDimension)) {
            throw new IllegalStateException("Milvus集合维度自动校准失败: oldDimension="
                    + currentDimension + ", requiredDimension=" + requiredDimension);
        }
        return new DimensionSyncResult(true, currentDimension, requiredDimension, previousEntityCount);
    }

    /**
     * 获取当前集合实际使用的向量维度。
     * 如果集合不存在或无法获取，返回配置中的维度。
     */
    public int getActualDimension() {
        int cached = actualDimension;
        if (cached > 0) {
            return cached;
        }
        int described = describeDimension(milvusConfig.getCollectionName());
        if (described > 0) {
            actualDimension = described;
            milvusConfig.setDimension(described);
            return described;
        }
        return milvusConfig.getDimension();
    }

    private int describeDimension(String collectionName) {
        try {
            DescribeCollectionResp response = client.describeCollection(DescribeCollectionReq.builder()
                    .collectionName(collectionName)
                    .build());
            if (response == null || response.getCollectionSchema() == null) {
                return -1;
            }
            CreateCollectionReq.FieldSchema field = response.getCollectionSchema().getField("dense_vector");
            return field != null && field.getDimension() != null ? field.getDimension() : -1;
        } catch (Exception e) {
            log.warn("读取Milvus集合维度失败: collection={}, error={}", collectionName, e.getMessage());
            return -1;
        }
    }

    private int validateVectorDimensions(List<VectorDocument> documents) {
        int dimension = -1;
        for (VectorDocument document : documents) {
            float[] vector = document.getVector();
            if (vector == null || vector.length == 0) {
                throw new IllegalArgumentException("存在空向量，无法写入Milvus");
            }
            if (dimension < 0) {
                dimension = vector.length;
            } else if (dimension != vector.length) {
                throw new IllegalArgumentException("同一批次存在不同向量维度: expected="
                        + dimension + ", actual=" + vector.length);
            }
        }
        return dimension;
    }

    public record DimensionSyncResult(boolean rebuilt, int previousDimension,
                                      int currentDimension, long previousEntityCount) {
        public static DimensionSyncResult unchanged(int dimension) {
            return new DimensionSyncResult(false, dimension, dimension, 0);
        }

        public boolean requiresFullReindex() {
            return rebuilt && previousEntityCount > 0;
        }
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
