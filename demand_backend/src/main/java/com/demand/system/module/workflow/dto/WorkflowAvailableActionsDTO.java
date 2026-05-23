package com.demand.system.module.workflow.dto;

import java.util.List;

public class WorkflowAvailableActionsDTO {

    private Boolean canTransition;

    private Boolean canRollback;

    private Boolean canCancel;

    private String currentNodeId;

    private String currentNodeName;

    private String currentNodeType;

    private String currentNodeStatusCode;

    private String currentNodeStatusName;

    private List<AvailableTransitionDTO> transitions;

    private Integer lockVersion;

    /** 当前处于审批节点且需要提交评价后方可流转 */
    private Boolean evaluationRequired;

    public Boolean getCanTransition() {
        return canTransition;
    }

    public void setCanTransition(Boolean canTransition) {
        this.canTransition = canTransition;
    }

    public Boolean getCanRollback() {
        return canRollback;
    }

    public void setCanRollback(Boolean canRollback) {
        this.canRollback = canRollback;
    }

    public Boolean getCanCancel() {
        return canCancel;
    }

    public void setCanCancel(Boolean canCancel) {
        this.canCancel = canCancel;
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

    public String getCurrentNodeType() {
        return currentNodeType;
    }

    public void setCurrentNodeType(String currentNodeType) {
        this.currentNodeType = currentNodeType;
    }

    public String getCurrentNodeStatusCode() {
        return currentNodeStatusCode;
    }

    public void setCurrentNodeStatusCode(String currentNodeStatusCode) {
        this.currentNodeStatusCode = currentNodeStatusCode;
    }

    public String getCurrentNodeStatusName() {
        return currentNodeStatusName;
    }

    public void setCurrentNodeStatusName(String currentNodeStatusName) {
        this.currentNodeStatusName = currentNodeStatusName;
    }

    public List<AvailableTransitionDTO> getTransitions() {
        return transitions;
    }

    public void setTransitions(List<AvailableTransitionDTO> transitions) {
        this.transitions = transitions;
    }

    public Integer getLockVersion() {
        return lockVersion;
    }

    public void setLockVersion(Integer lockVersion) {
        this.lockVersion = lockVersion;
    }

    public Boolean getEvaluationRequired() {
        return evaluationRequired;
    }

    public void setEvaluationRequired(Boolean evaluationRequired) {
        this.evaluationRequired = evaluationRequired;
    }
}
