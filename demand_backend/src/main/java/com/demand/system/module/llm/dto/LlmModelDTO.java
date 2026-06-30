package com.demand.system.module.llm.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public class LlmModelDTO {
    @NotBlank(message = "模型名称不能为空")
    private String name;
    @NotBlank(message = "模型标识不能为空")
    private String modelId;
    private String modelType = "general";
    /** 向量维度（仅 embedding 类型模型需要） */
    @Min(1) @Max(8192)
    private Integer dimension;
    private Integer contextWindow;
    private String ownedBy;
    private Long modelCreated;
    @DecimalMin("0.00") @DecimalMax("1.00")
    private BigDecimal temperature = new BigDecimal("0.30");
    @Min(1) @Max(128000)
    private Integer maxTokens = 2048;
    private Boolean isDefault = false;
    private Boolean enabled = true;
    /** 文本分块大小（仅 embedding 类型模型使用） */
    @Min(1) @Max(10000)
    private Integer chunkSize;
    /** 文本分块重叠大小（仅 embedding 类型模型使用） */
    @Min(0)
    private Integer chunkOverlap;
    /** 检索返回 TopK（仅 embedding 类型模型使用） */
    @Min(1) @Max(1000)
    private Integer searchTopK;

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
    public Integer getChunkSize() { return chunkSize; }
    public void setChunkSize(Integer chunkSize) { this.chunkSize = chunkSize; }
    public Integer getChunkOverlap() { return chunkOverlap; }
    public void setChunkOverlap(Integer chunkOverlap) { this.chunkOverlap = chunkOverlap; }
    public Integer getSearchTopK() { return searchTopK; }
    public void setSearchTopK(Integer searchTopK) { this.searchTopK = searchTopK; }
}
