package com.demand.system.module.workflow.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 工作流复制响应 DTO
 */
@Schema(description = "工作流复制响应")
public class WorkflowCopyResponse {

    @Schema(description = "新创建的工作流版本ID", example = "123")
    private Long workflowVersionId;

    @Schema(description = "新工作流名称", example = "审批流程 - 副本")
    private String name;

    @Schema(description = "新版本号", example = "v2.0")
    private String version;

    @Schema(description = "复制模式", example = "sync", allowableValues = {"sync", "async"})
    private String mode = "sync";

    @Schema(description = "提示消息", example = "工作流复制成功")
    private String message;

    @Schema(description = "复制的节点数量", example = "15")
    private Integer copiedNodeCount;

    @Schema(description = "复制的连线数量", example = "20")
    private Integer copiedEdgeCount;

    // Constructors

    public WorkflowCopyResponse() {
    }

    public WorkflowCopyResponse(Long workflowVersionId, String name, String version) {
        this.workflowVersionId = workflowVersionId;
        this.name = name;
        this.version = version;
        this.message = "工作流复制成功";
    }

    // Getters and Setters

    public Long getWorkflowVersionId() {
        return workflowVersionId;
    }

    public void setWorkflowVersionId(Long workflowVersionId) {
        this.workflowVersionId = workflowVersionId;
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

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Integer getCopiedNodeCount() {
        return copiedNodeCount;
    }

    public void setCopiedNodeCount(Integer copiedNodeCount) {
        this.copiedNodeCount = copiedNodeCount;
    }

    public Integer getCopiedEdgeCount() {
        return copiedEdgeCount;
    }

    public void setCopiedEdgeCount(Integer copiedEdgeCount) {
        this.copiedEdgeCount = copiedEdgeCount;
    }
}
