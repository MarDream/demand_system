package com.demand.system.module.llm.dto;

public class SniffedModelVO {
    private String modelId;
    private String ownedBy;
    private Long contextWindow;
    private Long created;
    private boolean alreadyExists;

    public String getModelId() { return modelId; }
    public void setModelId(String modelId) { this.modelId = modelId; }
    public String getOwnedBy() { return ownedBy; }
    public void setOwnedBy(String ownedBy) { this.ownedBy = ownedBy; }
    public Long getContextWindow() { return contextWindow; }
    public void setContextWindow(Long contextWindow) { this.contextWindow = contextWindow; }
    public Long getCreated() { return created; }
    public void setCreated(Long created) { this.created = created; }
    public boolean isAlreadyExists() { return alreadyExists; }
    public void setAlreadyExists(boolean alreadyExists) { this.alreadyExists = alreadyExists; }
}
