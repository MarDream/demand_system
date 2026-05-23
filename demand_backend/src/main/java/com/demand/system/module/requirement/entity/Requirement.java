package com.demand.system.module.requirement.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.demand.system.module.requirement.dto.RequirementAttachmentDTO;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@TableName(value = "requirements", autoResultMap = true)
public class Requirement {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;

    private Long parentId;

    private Long creatorId;

    private Long assigneeId;

    private Long opsFollowId;

    private Long maintFollowId;

    private Long departmentId;

    private Long orgId;

    private String requirementNo;

    private String title;

    private String description;

    private String type;

    private String priority;

    private String status;

    private Long moduleId;

    private Long iterationId;

    private Long workflowInstanceId;

    private String nodeStatus;

    private Boolean isDraft;

    private LocalDateTime lastSavedAt;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> creatorRoleCodes;

    private Boolean legacyWorkflow;

    private LocalDate startDate;

    private BigDecimal estimatedHours;

    private BigDecimal actualHours;

    private LocalDate dueDate;

    private LocalDateTime analysisCompletedAt;

    private LocalDateTime confirmAt;

    private LocalDateTime developmentCompletedAt;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Long> ccUserIds;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<RequirementAttachmentDTO> attachments;

    private Integer orderNum;

    private Integer version;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deletedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public Long getCreatorId() { return creatorId; }
    public void setCreatorId(Long creatorId) { this.creatorId = creatorId; }
    public Long getAssigneeId() { return assigneeId; }
    public void setAssigneeId(Long assigneeId) { this.assigneeId = assigneeId; }
    public Long getOpsFollowId() { return opsFollowId; }
    public void setOpsFollowId(Long opsFollowId) { this.opsFollowId = opsFollowId; }
    public Long getMaintFollowId() { return maintFollowId; }
    public void setMaintFollowId(Long maintFollowId) { this.maintFollowId = maintFollowId; }
    public Long getDepartmentId() { return departmentId; }
    public void setDepartmentId(Long departmentId) { this.departmentId = departmentId; }
    public Long getOrgId() { return orgId; }
    public void setOrgId(Long orgId) { this.orgId = orgId; }
    public String getRequirementNo() { return requirementNo; }
    public void setRequirementNo(String requirementNo) { this.requirementNo = requirementNo; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getModuleId() { return moduleId; }
    public void setModuleId(Long moduleId) { this.moduleId = moduleId; }
    public Long getIterationId() { return iterationId; }
    public void setIterationId(Long iterationId) { this.iterationId = iterationId; }
    public Long getWorkflowInstanceId() { return workflowInstanceId; }
    public void setWorkflowInstanceId(Long workflowInstanceId) { this.workflowInstanceId = workflowInstanceId; }
    public String getNodeStatus() { return nodeStatus; }
    public void setNodeStatus(String nodeStatus) { this.nodeStatus = nodeStatus; }
    public Boolean getIsDraft() { return isDraft; }
    public void setIsDraft(Boolean isDraft) { this.isDraft = isDraft; }
    public LocalDateTime getLastSavedAt() { return lastSavedAt; }
    public void setLastSavedAt(LocalDateTime lastSavedAt) { this.lastSavedAt = lastSavedAt; }
    public List<String> getCreatorRoleCodes() { return creatorRoleCodes; }
    public void setCreatorRoleCodes(List<String> creatorRoleCodes) { this.creatorRoleCodes = creatorRoleCodes; }
    public Boolean getLegacyWorkflow() { return legacyWorkflow; }
    public void setLegacyWorkflow(Boolean legacyWorkflow) { this.legacyWorkflow = legacyWorkflow; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public BigDecimal getEstimatedHours() { return estimatedHours; }
    public void setEstimatedHours(BigDecimal estimatedHours) { this.estimatedHours = estimatedHours; }
    public BigDecimal getActualHours() { return actualHours; }
    public void setActualHours(BigDecimal actualHours) { this.actualHours = actualHours; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    public LocalDateTime getAnalysisCompletedAt() { return analysisCompletedAt; }
    public void setAnalysisCompletedAt(LocalDateTime analysisCompletedAt) { this.analysisCompletedAt = analysisCompletedAt; }
    public LocalDateTime getConfirmAt() { return confirmAt; }
    public void setConfirmAt(LocalDateTime confirmAt) { this.confirmAt = confirmAt; }
    public LocalDateTime getDevelopmentCompletedAt() { return developmentCompletedAt; }
    public void setDevelopmentCompletedAt(LocalDateTime developmentCompletedAt) { this.developmentCompletedAt = developmentCompletedAt; }
    public List<Long> getCcUserIds() { return ccUserIds; }
    public void setCcUserIds(List<Long> ccUserIds) { this.ccUserIds = ccUserIds; }
    public List<RequirementAttachmentDTO> getAttachments() { return attachments; }
    public void setAttachments(List<RequirementAttachmentDTO> attachments) { this.attachments = attachments; }
    public Integer getOrderNum() { return orderNum; }
    public void setOrderNum(Integer orderNum) { this.orderNum = orderNum; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Integer getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Integer deletedAt) { this.deletedAt = deletedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Requirement that = (Requirement) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
