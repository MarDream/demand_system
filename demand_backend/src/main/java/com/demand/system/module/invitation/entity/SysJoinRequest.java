package com.demand.system.module.invitation.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 用户加入申请记录
 * <p>
 * 来源有四类：邀请链接、批量邀请、管理员直接添加、用户自助申请。
 * 审批通过后会据此创建账号并归入组织。
 */
@TableName("sys_join_requests")
public class SysJoinRequest {

    /** 来源：邀请链接 */
    public static final String SOURCE_LINK = "link";

    /** 来源：批量邀请 */
    public static final String SOURCE_BATCH = "batch";

    /** 来源：管理员直接添加 */
    public static final String SOURCE_ADMIN = "admin";

    /** 来源：用户自助申请 */
    public static final String SOURCE_SELF = "self";

    /** 状态：待处理 */
    public static final String STATUS_PENDING = "pending";

    /** 状态：已通过 */
    public static final String STATUS_APPROVED = "approved";

    /** 状态：已拒绝 */
    public static final String STATUS_REJECTED = "rejected";

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long invitationId;

    private Long userId;

    private String applicantName;

    private String applicantPhone;

    private String applicantEmail;

    private Long orgId;

    private String roleIds;

    private String source;

    private String status;

    private String applyRemark;

    private Long reviewedBy;

    private LocalDateTime reviewedAt;

    private String reviewRemark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deletedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getInvitationId() { return invitationId; }
    public void setInvitationId(Long invitationId) { this.invitationId = invitationId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getApplicantName() { return applicantName; }
    public void setApplicantName(String applicantName) { this.applicantName = applicantName; }

    public String getApplicantPhone() { return applicantPhone; }
    public void setApplicantPhone(String applicantPhone) { this.applicantPhone = applicantPhone; }

    public String getApplicantEmail() { return applicantEmail; }
    public void setApplicantEmail(String applicantEmail) { this.applicantEmail = applicantEmail; }

    public Long getOrgId() { return orgId; }
    public void setOrgId(Long orgId) { this.orgId = orgId; }

    public String getRoleIds() { return roleIds; }
    public void setRoleIds(String roleIds) { this.roleIds = roleIds; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getApplyRemark() { return applyRemark; }
    public void setApplyRemark(String applyRemark) { this.applyRemark = applyRemark; }

    public Long getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(Long reviewedBy) { this.reviewedBy = reviewedBy; }

    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }

    public String getReviewRemark() { return reviewRemark; }
    public void setReviewRemark(String reviewRemark) { this.reviewRemark = reviewRemark; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public Integer getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Integer deletedAt) { this.deletedAt = deletedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SysJoinRequest that = (SysJoinRequest) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
