package com.demand.system.module.requirement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class RequirementCreateDTO {

    @NotNull(message = "项目ID不能为空")
    private Long projectId;

    private Long parentId;

    @NotBlank(message = "标题不能为空")
    @Size(max = 200, message = "标题长度不能超过200字符")
    private String title;

    @Size(max = 5000, message = "描述长度不能超过5000字符")
    private String description;

    private String type;

    @NotBlank(message = "优先级不能为空")
    private String priority;

    private Long assigneeId;

    private List<Long> ccUserIds;

    private Long iterationId;

    private Long moduleId;

    private LocalDate startDate;

    private LocalDate dueDate;

    private BigDecimal estimatedHours;

    private List<RequirementAttachmentDTO> attachments;

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public Long getAssigneeId() {
        return assigneeId;
    }

    public void setAssigneeId(Long assigneeId) {
        this.assigneeId = assigneeId;
    }

    public List<Long> getCcUserIds() {
        return ccUserIds;
    }

    public void setCcUserIds(List<Long> ccUserIds) {
        this.ccUserIds = ccUserIds;
    }

    public Long getIterationId() {
        return iterationId;
    }

    public void setIterationId(Long iterationId) {
        this.iterationId = iterationId;
    }

    public Long getModuleId() {
        return moduleId;
    }

    public void setModuleId(Long moduleId) {
        this.moduleId = moduleId;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public BigDecimal getEstimatedHours() {
        return estimatedHours;
    }

    public void setEstimatedHours(BigDecimal estimatedHours) {
        this.estimatedHours = estimatedHours;
    }

    public List<RequirementAttachmentDTO> getAttachments() {
        return attachments;
    }

    public void setAttachments(List<RequirementAttachmentDTO> attachments) {
        this.attachments = attachments;
    }
}
