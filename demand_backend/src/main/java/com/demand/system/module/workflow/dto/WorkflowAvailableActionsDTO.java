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

    /** 当前节点启用会签 */
    private Boolean countersignEnabled;

    /** 当前用户有待处理的会签任务 */
    private Boolean canCountersign;

    /** 会签尚未满足流转条件 */
    private Boolean countersignPending;

    /** 当前处于并行分支执行中 */
    private Boolean parallelActive;

    /** 当前激活的并行分支 ID */
    private Long activeParallelBranchId;

    /** 并行分支列表 */
    private java.util.List<ParallelBranchVO> parallelBranches;

    /** 修复 P2：当前节点是否要求必填审批意见（来自节点 properties.requireComment） */
    private Boolean currentNodeRequireComment;

    /** 当前节点是否要求必须上传附件（来自节点 properties.requireAttachment） */
    private Boolean currentNodeRequireAttachment;

    /** 当前节点的评分配置（来自节点 properties.ratingConfig，对应 ADR-002） */
    private Object currentNodeRatingConfig;

    /** 当前用户是否可编辑此需求（基于工作流节点权限判断） */
    private Boolean canEdit;

    /** 当前用户是否可删除此需求（仅创建人或管理员，且在可操作节点上） */
    private Boolean canDelete;

    /** 当前用户是否可拆分子需求（与 canEdit 一致） */
    private Boolean canSplit;

    /** 关联工作流版本当前是否处于启用状态（is_active=1）。
     *  当为 false 时，所有 canTransition/canRollback/canCancel 都会是 false 且 transitions 为空，
     *  表示工作流被管理员停用，不允许执行任何流转操作。 */
    private Boolean workflowActive;

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

    public Boolean getCountersignEnabled() {
        return countersignEnabled;
    }

    public void setCountersignEnabled(Boolean countersignEnabled) {
        this.countersignEnabled = countersignEnabled;
    }

    public Boolean getCanCountersign() {
        return canCountersign;
    }

    public void setCanCountersign(Boolean canCountersign) {
        this.canCountersign = canCountersign;
    }

    public Boolean getCountersignPending() {
        return countersignPending;
    }

    public void setCountersignPending(Boolean countersignPending) {
        this.countersignPending = countersignPending;
    }

    public Boolean getParallelActive() {
        return parallelActive;
    }

    public void setParallelActive(Boolean parallelActive) {
        this.parallelActive = parallelActive;
    }

    public Long getActiveParallelBranchId() {
        return activeParallelBranchId;
    }

    public void setActiveParallelBranchId(Long activeParallelBranchId) {
        this.activeParallelBranchId = activeParallelBranchId;
    }

    public java.util.List<ParallelBranchVO> getParallelBranches() {
        return parallelBranches;
    }

    public void setParallelBranches(java.util.List<ParallelBranchVO> parallelBranches) {
        this.parallelBranches = parallelBranches;
    }

    public Boolean getCurrentNodeRequireComment() {
        return currentNodeRequireComment;
    }

    public void setCurrentNodeRequireComment(Boolean currentNodeRequireComment) {
        this.currentNodeRequireComment = currentNodeRequireComment;
    }

    public Boolean getCurrentNodeRequireAttachment() {
        return currentNodeRequireAttachment;
    }

    public void setCurrentNodeRequireAttachment(Boolean currentNodeRequireAttachment) {
        this.currentNodeRequireAttachment = currentNodeRequireAttachment;
    }

    public Object getCurrentNodeRatingConfig() {
        return currentNodeRatingConfig;
    }

    public void setCurrentNodeRatingConfig(Object currentNodeRatingConfig) {
        this.currentNodeRatingConfig = currentNodeRatingConfig;
    }

    public Boolean getCanEdit() {
        return canEdit;
    }

    public void setCanEdit(Boolean canEdit) {
        this.canEdit = canEdit;
    }

    public Boolean getCanDelete() {
        return canDelete;
    }

    public void setCanDelete(Boolean canDelete) {
        this.canDelete = canDelete;
    }

    public Boolean getCanSplit() {
        return canSplit;
    }

    public void setCanSplit(Boolean canSplit) {
        this.canSplit = canSplit;
    }

    public Boolean getWorkflowActive() {
        return workflowActive;
    }

    public void setWorkflowActive(Boolean workflowActive) {
        this.workflowActive = workflowActive;
    }
}
