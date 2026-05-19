package com.demand.system.module.llm.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class LlmModelVO {
    private Long id;
    private Long providerId;
    private String name;
    private String modelId;
    private String modelType;
    private BigDecimal temperature;
    private Integer maxTokens;
    private Boolean isDefault;
    private Boolean enabled;
    private Boolean testSuccess;
    private Integer testDuration;
    private String testError;
    private LocalDateTime testAt;
    private LocalDateTime createdAt;
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
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
