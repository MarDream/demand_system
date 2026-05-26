package com.demand.system.module.workflow.dto;

import java.util.List;
import java.util.Map;

public class WorkflowNodeDTO {

    private String nodeId;

    private String nodeType;

    private String nodeName;

    private Integer positionX;

    private Integer positionY;

    private String assigneeType;

    private Integer assigneeRoleId;

    private Long assigneeRoleGroupId;

    private Long assigneeOrgId;

    private List<Long> assigneeUserIds;

    private Integer timeoutHours;

    private String timeoutAction;

    private Map<String, Object> properties;

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public String getNodeType() {
        return nodeType;
    }

    public void setNodeType(String nodeType) {
        this.nodeType = nodeType;
    }

    public String getNodeName() {
        return nodeName;
    }

    public void setNodeName(String nodeName) {
        this.nodeName = nodeName;
    }

    public Integer getPositionX() {
        return positionX;
    }

    public void setPositionX(Integer positionX) {
        this.positionX = positionX;
    }

    public Integer getPositionY() {
        return positionY;
    }

    public void setPositionY(Integer positionY) {
        this.positionY = positionY;
    }

    public String getAssigneeType() {
        return assigneeType;
    }

    public void setAssigneeType(String assigneeType) {
        this.assigneeType = assigneeType;
    }

    public Integer getAssigneeRoleId() {
        return assigneeRoleId;
    }

    public void setAssigneeRoleId(Integer assigneeRoleId) {
        this.assigneeRoleId = assigneeRoleId;
    }

    public Long getAssigneeRoleGroupId() {
        return assigneeRoleGroupId;
    }

    public void setAssigneeRoleGroupId(Long assigneeRoleGroupId) {
        this.assigneeRoleGroupId = assigneeRoleGroupId;
    }

    public Long getAssigneeOrgId() {
        return assigneeOrgId;
    }

    public void setAssigneeOrgId(Long assigneeOrgId) {
        this.assigneeOrgId = assigneeOrgId;
    }

    public List<Long> getAssigneeUserIds() {
        return assigneeUserIds;
    }

    public void setAssigneeUserIds(List<Long> assigneeUserIds) {
        this.assigneeUserIds = assigneeUserIds;
    }

    public Integer getTimeoutHours() {
        return timeoutHours;
    }

    public void setTimeoutHours(Integer timeoutHours) {
        this.timeoutHours = timeoutHours;
    }

    public String getTimeoutAction() {
        return timeoutAction;
    }

    public void setTimeoutAction(String timeoutAction) {
        this.timeoutAction = timeoutAction;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, Object> properties) {
        this.properties = properties;
    }
}
