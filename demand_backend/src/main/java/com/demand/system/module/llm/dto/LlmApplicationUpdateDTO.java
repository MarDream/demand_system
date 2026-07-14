package com.demand.system.module.llm.dto;

public class LlmApplicationUpdateDTO {
    private Long modelId;
    private Boolean enabled;

    public Long getModelId() { return modelId; }
    public void setModelId(Long modelId) { this.modelId = modelId; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
}
