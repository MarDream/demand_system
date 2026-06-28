package com.demand.system.module.workflow.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 工作流模板列表 DTO
 */
@Schema(description = "工作流模板信息")
public class WorkflowTemplateDTO {

    @Schema(description = "工作流版本ID", example = "1")
    private Long id;

    @Schema(description = "工作流名称", example = "项目审批流程")
    private String name;

    @Schema(description = "版本号", example = "v1.0")
    private String version;

    @Schema(description = "描述信息")
    private String description;

    @Schema(description = "是否为官方模板", example = "true")
    private Boolean isTemplate;

    @Schema(description = "被复制次数", example = "25")
    private Integer copyCount;

    @Schema(description = "创建人ID", example = "1001")
    private Long creatorId;

    @Schema(description = "创建人姓名", example = "张三")
    private String creatorName;

    @Schema(description = "项目ID", example = "10")
    private Long projectId;

    @Schema(description = "项目名称", example = "需求管理系统")
    private String projectName;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "最后使用时间")
    private LocalDateTime lastUsedAt;

    @Schema(description = "预览图URL")
    private String previewImage;

    @Schema(description = "节点数量", example = "8")
    private Integer nodeCount;

    @Schema(description = "激活状态", example = "active")
    private String activationStatus;

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Boolean getIsTemplate() {
        return isTemplate;
    }

    public void setIsTemplate(Boolean isTemplate) {
        this.isTemplate = isTemplate;
    }

    public Integer getCopyCount() {
        return copyCount;
    }

    public void setCopyCount(Integer copyCount) {
        this.copyCount = copyCount;
    }

    public Long getCreatorId() {
        return creatorId;
    }

    public void setCreatorId(Long creatorId) {
        this.creatorId = creatorId;
    }

    public String getCreatorName() {
        return creatorName;
    }

    public void setCreatorName(String creatorName) {
        this.creatorName = creatorName;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getLastUsedAt() {
        return lastUsedAt;
    }

    public void setLastUsedAt(LocalDateTime lastUsedAt) {
        this.lastUsedAt = lastUsedAt;
    }

    public String getPreviewImage() {
        return previewImage;
    }

    public void setPreviewImage(String previewImage) {
        this.previewImage = previewImage;
    }

    public Integer getNodeCount() {
        return nodeCount;
    }

    public void setNodeCount(Integer nodeCount) {
        this.nodeCount = nodeCount;
    }

    public String getActivationStatus() {
        return activationStatus;
    }

    public void setActivationStatus(String activationStatus) {
        this.activationStatus = activationStatus;
    }
}
