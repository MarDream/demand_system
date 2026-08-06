package com.demand.system.module.workflow.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 工作流定义列表 DTO（独立工作流实体，承载工作流名称）。
 * <p>
 * 注意：与 {@code WorkflowDefinitionDTO}（画布节点/连线配置）不同，本类表示工作流定义实体本身。
 */
@Schema(description = "工作流定义信息")
public class WorkflowDefinitionInfoDTO {

    @Schema(description = "工作流定义ID", example = "1")
    private Long id;

    @Schema(description = "工作流名称", example = "标准审批流程")
    private String name;

    @Schema(description = "归属项目ID(0=全局工作流)", example = "0")
    private Long projectId;

    @Schema(description = "归属项目名称", example = "全局流程")
    private String projectName;

    @Schema(description = "工作流描述")
    private String description;

    @Schema(description = "版本总数", example = "3")
    private Integer versionCount;

    @Schema(description = "已启用版本数", example = "1")
    private Integer activeVersionCount;

    @Schema(description = "创建人ID", example = "1001")
    private Long creatorId;

    @Schema(description = "创建人姓名", example = "张三")
    private String creatorName;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getVersionCount() {
        return versionCount;
    }

    public void setVersionCount(Integer versionCount) {
        this.versionCount = versionCount;
    }

    public Integer getActiveVersionCount() {
        return activeVersionCount;
    }

    public void setActiveVersionCount(Integer activeVersionCount) {
        this.activeVersionCount = activeVersionCount;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
