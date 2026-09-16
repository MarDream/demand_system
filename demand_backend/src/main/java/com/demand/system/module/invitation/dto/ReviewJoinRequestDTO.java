package com.demand.system.module.invitation.dto;

import java.util.List;

/**
 * 审批加入申请入参。
 * <p>
 * orgId / roleIds 允许审批人在通过时覆盖邀请里预分配的值；
 * 传 null 表示沿用申请记录上已有的值。
 */
public class ReviewJoinRequestDTO {

    private Long orgId;

    private List<Long> roleIds;

    private String reviewRemark;

    public Long getOrgId() { return orgId; }
    public void setOrgId(Long orgId) { this.orgId = orgId; }

    public List<Long> getRoleIds() { return roleIds; }
    public void setRoleIds(List<Long> roleIds) { this.roleIds = roleIds; }

    public String getReviewRemark() { return reviewRemark; }
    public void setReviewRemark(String reviewRemark) { this.reviewRemark = reviewRemark; }
}
