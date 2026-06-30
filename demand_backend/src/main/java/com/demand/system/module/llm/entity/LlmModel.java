package com.demand.system.module.llm.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

@TableName("llm_models")
public class LlmModel {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long providerId;
    private String name;
    private String modelId;
    private String modelType;
    private Integer dimension;
    private Integer contextWindow;
    private String ownedBy;
    private Long modelCreated;
    private BigDecimal temperature;
    private Integer maxTokens;
    private Boolean isDefault;
    private Boolean enabled;
    private Boolean testSuccess;
    private Integer testDuration;
    private String testError;
    private LocalDateTime testAt;
    private Integer chunkSize;
    private Integer chunkOverlap;
    private Integer searchTopK;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProviderId() { return providerId; }
    public void setProviderId(Long providerId) { this.providerId = providerId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getModelId() { return modelId; }
    public void setModelId(String modelId) { this.modelId = modelId; }
    public String getModelType() { return modelType; }
    public void setModelType(String modelType) { this.modelType = modelType; }
    public Integer getDimension() { return dimension; }
    public void setDimension(Integer dimension) { this.dimension = dimension; }
    public Integer getContextWindow() { return contextWindow; }
    public void setContextWindow(Integer contextWindow) { this.contextWindow = contextWindow; }
    public String getOwnedBy() { return ownedBy; }
    public void setOwnedBy(String ownedBy) { this.ownedBy = ownedBy; }
    public Long getModelCreated() { return modelCreated; }
    public void setModelCreated(Long modelCreated) { this.modelCreated = modelCreated; }
    public BigDecimal getTemperature() { return temperature; }
    public void setTemperature(BigDecimal temperature) { this.temperature = temperature; }
    public Integer getMaxTokens() { return maxTokens; }
    public void setMaxTokens(Integer maxTokens) { this.maxTokens = maxTokens; }
    public Boolean getIsDefault() { return isDefault; }
    public void setIsDefault(Boolean isDefault) { this.isDefault = isDefault; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public Boolean getTestSuccess() { return testSuccess; }
    public void setTestSuccess(Boolean testSuccess) { this.testSuccess = testSuccess; }
    public Integer getTestDuration() { return testDuration; }
    public void setTestDuration(Integer testDuration) { this.testDuration = testDuration; }
    public String getTestError() { return testError; }
    public void setTestError(String testError) { this.testError = testError; }
    public LocalDateTime getTestAt() { return testAt; }
    public void setTestAt(LocalDateTime testAt) { this.testAt = testAt; }
    public Integer getChunkSize() { return chunkSize; }
    public void setChunkSize(Integer chunkSize) { this.chunkSize = chunkSize; }
    public Integer getChunkOverlap() { return chunkOverlap; }
    public void setChunkOverlap(Integer chunkOverlap) { this.chunkOverlap = chunkOverlap; }
    public Integer getSearchTopK() { return searchTopK; }
    public void setSearchTopK(Integer searchTopK) { this.searchTopK = searchTopK; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LlmModel llmModel = (LlmModel) o;
        return Objects.equals(id, llmModel.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
