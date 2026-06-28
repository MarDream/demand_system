package com.demand.system.module.workflow.dto;

import java.util.List;

/**
 * 列表页当前节点处理人信息（轻量）
 *
 * 用于需求列表页批量查询，避免对每条工单单独调用 getAvailableActions。
 * 核心逻辑：
 * - SPECIFIED_ROLE 且角色仅 1 人 → display = 用户姓名
 * - SPECIFIED_ROLE 且角色多人   → display = 角色名称
 * - 其他 assigneeType          → display = 候选人名称 或 类型名称
 */
public class CurrentNodeHandlerDTO {

    private Long requirementId;

    /** 当前节点处理人显示名（列表页"负责人"列直接使用） */
    private String display;

    /** 处理人类型：SPECIFIED_USER / SPECIFIED_ROLE / CREATOR / PREV_APPROVER 等 */
    private String assigneeType;

    /** 处理人类型中文名：指定用户 / 指定角色 / 提交人 等 */
    private String assigneeTypeName;

    /** 当前节点 ID */
    private String currentNodeId;

    /** 当前节点名称 */
    private String currentNodeName;

    /** 当前节点关联的候选用户（用于判断角色下人数） */
    private List<AssigneeCandidateDTO> candidates;

    // Getters & Setters

    public Long getRequirementId() {
        return requirementId;
    }

    public void setRequirementId(Long requirementId) {
        this.requirementId = requirementId;
    }

    public String getDisplay() {
        return display;
    }

    public void setDisplay(String display) {
        this.display = display;
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

    public String getCurrentNodeId() {
        return currentNodeId;
    }

    public void setCurrentNodeId(String currentNodeId) {
        this.currentNodeId = currentNodeId;
    }

    public String getCurrentNodeName() {
        return currentNodeName;
    }

    public void setCurrentNodeName(String currentNodeName) {
        this.currentNodeName = currentNodeName;
    }

    public List<AssigneeCandidateDTO> getCandidates() {
        return candidates;
    }

    public void setCandidates(List<AssigneeCandidateDTO> candidates) {
        this.candidates = candidates;
    }
}
