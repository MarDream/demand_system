package com.demand.system.module.requirement.dto;

import java.time.LocalDateTime;

/**
 * 需求列表页精简 VO
 *
 * 仅包含列表页展示的字段，排除 description、attachments、ccUserIds 等大字段，
 * 减少 IO 50-70%，加速序列化和传输。
 *
 * 详情页继续使用完整的 RequirementVO。
 */
public class RequirementListVO {

    private Long id;
    private String requirementNo;
    private String title;
    private String type;
    private String priority;
    private String status;
    private Long orgId;
    private Long creatorId;
    private Long assigneeId;
    private Long opsFollowId;
    private Long maintFollowId;
    private Boolean isDraft;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 关联填充字段（由 batchFillUserNamesAndOrg 回填）
    private String creatorName;
    private String assigneeName;
    private String opsFollowName;
    private String maintFollowName;
    private String departmentName;
    private Boolean followed;

    // 权限字段（前端操作按钮显示控制）
    private Boolean canEdit;
    private String operationType; // 'edit' | 'approve' | 'view' （待办/已办视图使用）

    // Getters and Setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRequirementNo() { return requirementNo; }
    public void setRequirementNo(String requirementNo) { this.requirementNo = requirementNo; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getOrgId() { return orgId; }
    public void setOrgId(Long orgId) { this.orgId = orgId; }
    public Long getCreatorId() { return creatorId; }
    public void setCreatorId(Long creatorId) { this.creatorId = creatorId; }
    public Long getAssigneeId() { return assigneeId; }
    public void setAssigneeId(Long assigneeId) { this.assigneeId = assigneeId; }
    public Long getOpsFollowId() { return opsFollowId; }
    public void setOpsFollowId(Long opsFollowId) { this.opsFollowId = opsFollowId; }
    public Long getMaintFollowId() { return maintFollowId; }
    public void setMaintFollowId(Long maintFollowId) { this.maintFollowId = maintFollowId; }
    public Boolean getIsDraft() { return isDraft; }
    public void setIsDraft(Boolean isDraft) { this.isDraft = isDraft; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public String getCreatorName() { return creatorName; }
    public void setCreatorName(String creatorName) { this.creatorName = creatorName; }
    public String getAssigneeName() { return assigneeName; }
    public void setAssigneeName(String assigneeName) { this.assigneeName = assigneeName; }
    public String getOpsFollowName() { return opsFollowName; }
    public void setOpsFollowName(String opsFollowName) { this.opsFollowName = opsFollowName; }
    public String getMaintFollowName() { return maintFollowName; }
    public void setMaintFollowName(String maintFollowName) { this.maintFollowName = maintFollowName; }
    public String getDepartmentName() { return departmentName; }
    public void setDepartmentName(String departmentName) { this.departmentName = departmentName; }
    public Boolean getFollowed() { return followed; }
    public void setFollowed(Boolean followed) { this.followed = followed; }
    public Boolean getCanEdit() { return canEdit; }
    public void setCanEdit(Boolean canEdit) { this.canEdit = canEdit; }
    public String getOperationType() { return operationType; }
    public void setOperationType(String operationType) { this.operationType = operationType; }
}
