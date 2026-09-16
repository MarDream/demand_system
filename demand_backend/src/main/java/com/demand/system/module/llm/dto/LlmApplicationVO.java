package com.demand.system.module.llm.dto;

public class LlmApplicationVO {
    private Long id;
    private String code;
    private String name;
    private String description;
    private String modelType;
    private Long modelId;
    private String modelName;
    private String modelCode;
    private String providerName;
    private Boolean modelAvailable;
    private Boolean enabled;
    private Integer sortOrder;
    /** 所属分组ID，null=未分组 */
    private Long groupId;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getModelType() { return modelType; }
    public void setModelType(String modelType) { this.modelType = modelType; }
    public Long getModelId() { return modelId; }
    public void setModelId(Long modelId) { this.modelId = modelId; }
    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }
    public String getModelCode() { return modelCode; }
    public void setModelCode(String modelCode) { this.modelCode = modelCode; }
    public String getProviderName() { return providerName; }
    public void setProviderName(String providerName) { this.providerName = providerName; }
    public Boolean getModelAvailable() { return modelAvailable; }
    public void setModelAvailable(Boolean modelAvailable) { this.modelAvailable = modelAvailable; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public Long getGroupId() { return groupId; }
    public void setGroupId(Long groupId) { this.groupId = groupId; }
}
