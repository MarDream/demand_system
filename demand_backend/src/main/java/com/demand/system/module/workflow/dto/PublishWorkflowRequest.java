package com.demand.system.module.workflow.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 发布工作流请求
 */
@Schema(description = "发布工作流请求")
public class PublishWorkflowRequest {

    @NotNull(message = "项目ID不能为空")
    @Schema(description = "项目ID")
    private Long projectId;

    @NotNull(message = "工单类型ID不能为空")
    @Schema(description = "工单类型ID")
    private Long requirementTypeId;

    @NotBlank(message = "工作流定义不能为空")
    @Schema(description = "工作流定义JSON")
    private String definition;

    @Schema(description = "版本变更说明")
    private String changeLog;

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public Long getRequirementTypeId() {
        return requirementTypeId;
    }

    public void setRequirementTypeId(Long requirementTypeId) {
        this.requirementTypeId = requirementTypeId;
    }

    public String getDefinition() {
        return definition;
    }

    public void setDefinition(String definition) {
        this.definition = definition;
    }

    public String getChangeLog() {
        return changeLog;
    }

    public void setChangeLog(String changeLog) {
        this.changeLog = changeLog;
    }
}
