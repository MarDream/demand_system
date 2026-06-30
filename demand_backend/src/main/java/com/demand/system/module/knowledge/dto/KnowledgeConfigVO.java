package com.demand.system.module.knowledge.dto;

import java.util.List;

/**
 * 知识库配置聚合 VO，一次性返回前端所需的全部配置状态。
 */
public class KnowledgeConfigVO {

    // ---- 分块参数 ----
    private int chunkSize;
    private int chunkOverlap;
    private int searchTopK;

    // ---- Milvus ----
    private int milvusDimension;

    // ---- Embedding 状态 ----
    private ModelStatus embedding;

    // ---- Reranker 状态 ----
    private ModelStatus reranker;

    // ---- Chat 状态 ----
    private ModelStatus chat;

    // ---- 候选列表 ----
    private List<ModelCandidate> embeddingCandidates;
    private List<ModelCandidate> rerankerCandidates;
    private List<ModelCandidate> chatCandidates;

    public int getChunkSize() { return chunkSize; }
    public void setChunkSize(int chunkSize) { this.chunkSize = chunkSize; }
    public int getChunkOverlap() { return chunkOverlap; }
    public void setChunkOverlap(int chunkOverlap) { this.chunkOverlap = chunkOverlap; }
    public int getSearchTopK() { return searchTopK; }
    public void setSearchTopK(int searchTopK) { this.searchTopK = searchTopK; }
    public int getMilvusDimension() { return milvusDimension; }
    public void setMilvusDimension(int milvusDimension) { this.milvusDimension = milvusDimension; }
    public ModelStatus getEmbedding() { return embedding; }
    public void setEmbedding(ModelStatus embedding) { this.embedding = embedding; }
    public ModelStatus getReranker() { return reranker; }
    public void setReranker(ModelStatus reranker) { this.reranker = reranker; }
    public ModelStatus getChat() { return chat; }
    public void setChat(ModelStatus chat) { this.chat = chat; }
    public List<ModelCandidate> getEmbeddingCandidates() { return embeddingCandidates; }
    public void setEmbeddingCandidates(List<ModelCandidate> embeddingCandidates) { this.embeddingCandidates = embeddingCandidates; }
    public List<ModelCandidate> getRerankerCandidates() { return rerankerCandidates; }
    public void setRerankerCandidates(List<ModelCandidate> rerankerCandidates) { this.rerankerCandidates = rerankerCandidates; }
    public List<ModelCandidate> getChatCandidates() { return chatCandidates; }
    public void setChatCandidates(List<ModelCandidate> chatCandidates) { this.chatCandidates = chatCandidates; }

    /**
     * 某类模型的当前状态
     */
    public static class ModelStatus {
        private boolean configured;   // 是否已配置可用模型
        private String modelId;       // 当前默认模型 ID（如 text-embedding-3-small）
        private String name;          // 模型名称（display name）
        private String providerName;  // 所属接入组名称
        private Integer dimension;    // 维度（仅 embedding 有意义）
        private Boolean dimensionMatch; // 维度是否与 Milvus 匹配（仅 embedding）
        private Boolean testSuccess;  // 最近测试是否成功
        private String testError;     // 最近测试失败原因

        public ModelStatus() {}

        /** 快速构造一个"未配置"状态 */
        public static ModelStatus notConfigured() {
            ModelStatus s = new ModelStatus();
            s.configured = false;
            return s;
        }

        public boolean isConfigured() { return configured; }
        public void setConfigured(boolean configured) { this.configured = configured; }
        public String getModelId() { return modelId; }
        public void setModelId(String modelId) { this.modelId = modelId; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getProviderName() { return providerName; }
        public void setProviderName(String providerName) { this.providerName = providerName; }
        public Integer getDimension() { return dimension; }
        public void setDimension(Integer dimension) { this.dimension = dimension; }
        public Boolean getDimensionMatch() { return dimensionMatch; }
        public void setDimensionMatch(Boolean dimensionMatch) { this.dimensionMatch = dimensionMatch; }
        public Boolean getTestSuccess() { return testSuccess; }
        public void setTestSuccess(Boolean testSuccess) { this.testSuccess = testSuccess; }
        public String getTestError() { return testError; }
        public void setTestError(String testError) { this.testError = testError; }
    }

    /**
     * 模型候选项（用于前端下拉）
     */
    public static class ModelCandidate {
        private Long id;
        private Long providerId;
        private String providerName;
        private String name;
        private String modelId;
        private String modelType;
        private Integer dimension;
        private Boolean isDefault;
        private Boolean enabled;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getProviderId() { return providerId; }
        public void setProviderId(Long providerId) { this.providerId = providerId; }
        public String getProviderName() { return providerName; }
        public void setProviderName(String providerName) { this.providerName = providerName; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getModelId() { return modelId; }
        public void setModelId(String modelId) { this.modelId = modelId; }
        public String getModelType() { return modelType; }
        public void setModelType(String modelType) { this.modelType = modelType; }
        public Integer getDimension() { return dimension; }
        public void setDimension(Integer dimension) { this.dimension = dimension; }
        public Boolean getIsDefault() { return isDefault; }
        public void setIsDefault(Boolean isDefault) { this.isDefault = isDefault; }
        public Boolean getEnabled() { return enabled; }
        public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    }
}
