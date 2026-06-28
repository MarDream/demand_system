package com.demand.system.module.workflow.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 工作流溯源树 DTO
 */
@Schema(description = "工作流溯源信息")
public class WorkflowLineageDTO {

    @Schema(description = "工作流版本ID")
    private Long id;

    @Schema(description = "工作流名称")
    private String name;

    @Schema(description = "版本号")
    private String version;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "创建人姓名")
    private String creatorName;

    @Schema(description = "源工作流（上一级）")
    private WorkflowLineageDTO source;

    @Schema(description = "是否循环引用")
    private Boolean isCircular;

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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getCreatorName() {
        return creatorName;
    }

    public void setCreatorName(String creatorName) {
        this.creatorName = creatorName;
    }

    public WorkflowLineageDTO getSource() {
        return source;
    }

    public void setSource(WorkflowLineageDTO source) {
        this.source = source;
    }

    public Boolean getIsCircular() {
        return isCircular;
    }

    public void setIsCircular(Boolean isCircular) {
        this.isCircular = isCircular;
    }
}
