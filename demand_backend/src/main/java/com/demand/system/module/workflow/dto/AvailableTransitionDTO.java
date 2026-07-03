package com.demand.system.module.workflow.dto;

import java.util.List;

public class AvailableTransitionDTO {

    private String toNodeId;

    private String toNodeName;

    private String label;

    private String bindStatusCode;

    private String bindStatusName;

    private Boolean projectRequired;

    private String assigneeType;

    private String assigneeTypeName;

    private String assigneeDisplayName;

    private List<AssigneeCandidateDTO> assigneeCandidates;

    private Long defaultAssigneeId;

    /**
     * 处理人作用域名称：根据 assigneeType 不同含义不同
     * <ul>
     *   <li>SPECIFIED_USER → "指定用户"</li>
     *   <li>SPECIFIED_ROLE → 角色名称（如"运维需求分析员"）</li>
     *   <li>SPECIFIED_ROLE_GROUP → 角色组名称（如"基础架构组"）</li>
     *   <li>SPECIFIED_ORG → "指定组织"</li>
     *   <li>CREATOR → "提交人"</li>
     *   <li>PREV_APPROVER → "上一节点处理人"</li>
     * </ul>
     * 用于前端审批侧边栏第一行展示，如"处理角色 运维需求分析员"
     */
    private String assigneeScopeName;

    public String getToNodeId() {
        return toNodeId;
    }

    public void setToNodeId(String toNodeId) {
        this.toNodeId = toNodeId;
    }

    public String getToNodeName() {
        return toNodeName;
    }

    public void setToNodeName(String toNodeName) {
        this.toNodeName = toNodeName;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getBindStatusCode() {
        return bindStatusCode;
    }

    public void setBindStatusCode(String bindStatusCode) {
        this.bindStatusCode = bindStatusCode;
    }

    public String getBindStatusName() {
        return bindStatusName;
    }

    public void setBindStatusName(String bindStatusName) {
        this.bindStatusName = bindStatusName;
    }

    public Boolean getProjectRequired() {
        return projectRequired;
    }

    public void setProjectRequired(Boolean projectRequired) {
        this.projectRequired = projectRequired;
    }

    public String getAssigneeType() {
        return assigneeType;
    }

    public void setAssigneeType(String assigneeType) {
        this.assigneeType = assigneeType;
    }

    public String getAssigneeTypeName() {
        return assigneeTypeName;
    }

    public void setAssigneeTypeName(String assigneeTypeName) {
        this.assigneeTypeName = assigneeTypeName;
    }

    public String getAssigneeDisplayName() {
        return assigneeDisplayName;
    }

    public void setAssigneeDisplayName(String assigneeDisplayName) {
        this.assigneeDisplayName = assigneeDisplayName;
    }

    public List<AssigneeCandidateDTO> getAssigneeCandidates() {
        return assigneeCandidates;
    }

    public void setAssigneeCandidates(List<AssigneeCandidateDTO> assigneeCandidates) {
        this.assigneeCandidates = assigneeCandidates;
    }

    public Long getDefaultAssigneeId() {
        return defaultAssigneeId;
    }

    public void setDefaultAssigneeId(Long defaultAssigneeId) {
        this.defaultAssigneeId = defaultAssigneeId;
    }

    public String getAssigneeScopeName() {
        return assigneeScopeName;
    }

    public void setAssigneeScopeName(String assigneeScopeName) {
        this.assigneeScopeName = assigneeScopeName;
    }
}
