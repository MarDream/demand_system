package com.demand.system.module.llm.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public class LlmModelDTO {
    @NotBlank(message = "模型名称不能为空")
    private String name;
    @NotBlank(message = "模型标识不能为空")
    private String modelId;
    private String modelType = "general";
    @DecimalMin("0.00") @DecimalMax("1.00")
    private BigDecimal temperature = new BigDecimal("0.30");
    @Min(1) @Max(128000)
    private Integer maxTokens = 2048;
    private Boolean isDefault = false;
    private Boolean enabled = true;

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
}
