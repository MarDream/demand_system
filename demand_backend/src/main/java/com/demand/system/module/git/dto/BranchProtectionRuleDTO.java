package com.demand.system.module.git.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public class BranchProtectionRuleDTO {

    @NotBlank(message = "规则名称不能为空")
    private String ruleName;

    private String branchPattern;

    private Integer priority;

    private Integer forbidPush;

    private Integer forbidForcePush;

    private Integer forbidDelete;

    private Integer requireMr;

    private Integer minApprovals;

    private Integer dismissStaleApprovals;

    private Integer blockSelfApprove;

    private Integer requireCodeownerApproval;

    private Integer requireThreadResolved;

    private Integer requireCiPass;

    private Integer requireUpToDate;

    private String ciContexts;

    private List<Long> whitelistUsers;

    private List<String> whitelistRoles;

    private Integer enabled;

    public String getRuleName() {
        return ruleName;
    }

    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
    }

    public String getBranchPattern() {
        return branchPattern;
    }

    public void setBranchPattern(String branchPattern) {
        this.branchPattern = branchPattern;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public Integer getForbidPush() {
        return forbidPush;
    }

    public void setForbidPush(Integer forbidPush) {
        this.forbidPush = forbidPush;
    }

    public Integer getForbidForcePush() {
        return forbidForcePush;
    }

    public void setForbidForcePush(Integer forbidForcePush) {
        this.forbidForcePush = forbidForcePush;
    }

    public Integer getForbidDelete() {
        return forbidDelete;
    }

    public void setForbidDelete(Integer forbidDelete) {
        this.forbidDelete = forbidDelete;
    }

    public Integer getRequireMr() {
        return requireMr;
    }

    public void setRequireMr(Integer requireMr) {
        this.requireMr = requireMr;
    }

    public Integer getMinApprovals() {
        return minApprovals;
    }

    public void setMinApprovals(Integer minApprovals) {
        this.minApprovals = minApprovals;
    }

    public Integer getDismissStaleApprovals() {
        return dismissStaleApprovals;
    }

    public void setDismissStaleApprovals(Integer dismissStaleApprovals) {
        this.dismissStaleApprovals = dismissStaleApprovals;
    }

    public Integer getBlockSelfApprove() {
        return blockSelfApprove;
    }

    public void setBlockSelfApprove(Integer blockSelfApprove) {
        this.blockSelfApprove = blockSelfApprove;
    }

    public Integer getRequireCodeownerApproval() {
        return requireCodeownerApproval;
    }

    public void setRequireCodeownerApproval(Integer requireCodeownerApproval) {
        this.requireCodeownerApproval = requireCodeownerApproval;
    }

    public Integer getRequireThreadResolved() {
        return requireThreadResolved;
    }

    public void setRequireThreadResolved(Integer requireThreadResolved) {
        this.requireThreadResolved = requireThreadResolved;
    }

    public Integer getRequireCiPass() {
        return requireCiPass;
    }

    public void setRequireCiPass(Integer requireCiPass) {
        this.requireCiPass = requireCiPass;
    }

    public Integer getRequireUpToDate() {
        return requireUpToDate;
    }

    public void setRequireUpToDate(Integer requireUpToDate) {
        this.requireUpToDate = requireUpToDate;
    }

    public String getCiContexts() {
        return ciContexts;
    }

    public void setCiContexts(String ciContexts) {
        this.ciContexts = ciContexts;
    }

    public List<Long> getWhitelistUsers() {
        return whitelistUsers;
    }

    public void setWhitelistUsers(List<Long> whitelistUsers) {
        this.whitelistUsers = whitelistUsers;
    }

    public List<String> getWhitelistRoles() {
        return whitelistRoles;
    }

    public void setWhitelistRoles(List<String> whitelistRoles) {
        this.whitelistRoles = whitelistRoles;
    }

    public Integer getEnabled() {
        return enabled;
    }

    public void setEnabled(Integer enabled) {
        this.enabled = enabled;
    }
}
