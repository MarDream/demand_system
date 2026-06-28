package com.demand.system.module.workflow.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 工作流版本视图对象
 */
@Schema(description = "工作流版本信息")
public class WorkflowVersionVO {

    @Schema(description = "版本ID")
    private Long id;

    @Schema(description = "项目ID")
    private Long projectId;

    @Schema(description = "项目名称")
    private String projectName;

    @Schema(description = "工单类型ID")
    private Long requirementTypeId;

    @Schema(description = "工单类型名称")
    private String requirementTypeName;

    @Schema(description = "版本号")
    private String version;

    @Schema(description = "版本名称")
    private String name;

    @Schema(description = "是否当前启用（0=否 1=是）")
    private Integer isActive;

    @Schema(description = "激活状态（draft/pending_approval/approved/active/deprecated/rejected）")
    private String activationStatus;

    @Schema(description = "激活状态名称")
    private String activationStatusName;

    @Schema(description = "启用时间")
    private LocalDateTime activatedAt;

    @Schema(description = "废弃时间")
    private LocalDateTime deprecatedAt;

    @Schema(description = "变更说明")
    private String changeLog;

    @Schema(description = "提交审批时间")
    private LocalDateTime submittedForApprovalAt;

    @Schema(description = "审批通过时间")
    private LocalDateTime approvedAt;

    @Schema(description = "审批人ID")
    private Long approvedBy;

    @Schema(description = "审批人姓名")
    private String approvedByName;

    @Schema(description = "审批意见")
    private String approvalComment;

    @Schema(description = "创建人ID")
    private Long creatorId;

    @Schema(description = "创建人姓名")
    private String creatorName;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "关联的运行中工单数")
    private Long runningInstanceCount;

    @Schema(description = "关联知识库ID")
    private Long knowledgeBaseId;

    @Schema(description = "关联知识库名称")
    private String knowledgeBaseName;

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public Long getRequirementTypeId() {
        return requirementTypeId;
    }

    public void setRequirementTypeId(Long requirementTypeId) {
        this.requirementTypeId = requirementTypeId;
    }

    public String getRequirementTypeName() {
        return requirementTypeName;
    }

    public void setRequirementTypeName(String requirementTypeName) {
        this.requirementTypeName = requirementTypeName;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getIsActive() {
        return isActive;
    }

    public void setIsActive(Integer isActive) {
        this.isActive = isActive;
    }

    public String getActivationStatus() {
        return activationStatus;
    }

    public void setActivationStatus(String activationStatus) {
        this.activationStatus = activationStatus;
    }

    public String getActivationStatusName() {
        return activationStatusName;
    }

    public void setActivationStatusName(String activationStatusName) {
        this.activationStatusName = activationStatusName;
    }

    public LocalDateTime getActivatedAt() {
        return activatedAt;
    }

    public void setActivatedAt(LocalDateTime activatedAt) {
        this.activatedAt = activatedAt;
    }

    public LocalDateTime getDeprecatedAt() {
        return deprecatedAt;
    }

    public void setDeprecatedAt(LocalDateTime deprecatedAt) {
        this.deprecatedAt = deprecatedAt;
    }

    public String getChangeLog() {
        return changeLog;
    }

    public void setChangeLog(String changeLog) {
        this.changeLog = changeLog;
    }

    public LocalDateTime getSubmittedForApprovalAt() {
        return submittedForApprovalAt;
    }

    public void setSubmittedForApprovalAt(LocalDateTime submittedForApprovalAt) {
        this.submittedForApprovalAt = submittedForApprovalAt;
    }

    public LocalDateTime getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(LocalDateTime approvedAt) {
        this.approvedAt = approvedAt;
    }

    public Long getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(Long approvedBy) {
        this.approvedBy = approvedBy;
    }

    public String getApprovedByName() {
        return approvedByName;
    }

    public void setApprovedByName(String approvedByName) {
        this.approvedByName = approvedByName;
    }

    public String getApprovalComment() {
        return approvalComment;
    }

    public void setApprovalComment(String approvalComment) {
        this.approvalComment = approvalComment;
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

    public Long getRunningInstanceCount() {
        return runningInstanceCount;
    }

    public void setRunningInstanceCount(Long runningInstanceCount) {
        this.runningInstanceCount = runningInstanceCount;
    }

    public Long getKnowledgeBaseId() {
        return knowledgeBaseId;
    }

    public void setKnowledgeBaseId(Long knowledgeBaseId) {
        this.knowledgeBaseId = knowledgeBaseId;
    }

    public String getKnowledgeBaseName() {
        return knowledgeBaseName;
    }

    public void setKnowledgeBaseName(String knowledgeBaseName) {
        this.knowledgeBaseName = knowledgeBaseName;
    }
}
