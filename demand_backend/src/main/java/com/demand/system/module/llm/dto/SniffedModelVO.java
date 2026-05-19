package com.demand.system.module.llm.dto;

public class SniffedModelVO {
    private String modelId;
    private String ownedBy;
    private boolean alreadyExists;

    public String getModelId() { return modelId; }
    public void setModelId(String modelId) { this.modelId = modelId; }
    public String getOwnedBy() { return ownedBy; }
    public void setOwnedBy(String ownedBy) { this.ownedBy = ownedBy; }
    public boolean isAlreadyExists() { return alreadyExists; }
    public void setAlreadyExists(boolean alreadyExists) { this.alreadyExists = alreadyExists; }
}
