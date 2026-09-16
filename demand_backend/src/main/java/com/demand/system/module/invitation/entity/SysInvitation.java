package com.demand.system.module.invitation.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 用户邀请记录（链接邀请 / 批量邀请）
 * <p>
 * 一条记录既是「邀请凭证」也是「邀请流水」：
 * 链接邀请的 invite_code 拼在 URL 上给被邀请人使用；
 * 批量邀请则每个被邀请人各生成一条，target 记手机号或邮箱。
 */
@TableName("sys_invitations")
public class SysInvitation {

    /** 邀请方式：通过链接邀请 */
    public static final String TYPE_LINK = "link";

    /** 邀请方式：批量邀请 */
    public static final String TYPE_BATCH = "batch";

    /** 状态：待接受 */
    public static final String STATUS_PENDING = "pending";

    /** 状态：已接受（对应申请已审批通过） */
    public static final String STATUS_ACCEPTED = "accepted";

    /** 状态：已过期 */
    public static final String STATUS_EXPIRED = "expired";

    /** 状态：已撤回 */
    public static final String STATUS_REVOKED = "revoked";

    @TableId(type = IdType.AUTO)
    private Long id;

    private String inviteCode;

    private String inviteType;

    private String target;

    private String targetName;

    private Long orgId;

    private String roleIds;

    private String status;

    private Integer maxUses;

    private Integer usedCount;

    private LocalDateTime expiresAt;

    private Long invitedBy;

    private Long acceptedBy;

    private LocalDateTime acceptedAt;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deletedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getInviteCode() { return inviteCode; }
    public void setInviteCode(String inviteCode) { this.inviteCode = inviteCode; }

    public String getInviteType() { return inviteType; }
    public void setInviteType(String inviteType) { this.inviteType = inviteType; }

    public String getTarget() { return target; }
    public void setTarget(String target) { this.target = target; }

    public String getTargetName() { return targetName; }
    public void setTargetName(String targetName) { this.targetName = targetName; }

    public Long getOrgId() { return orgId; }
    public void setOrgId(Long orgId) { this.orgId = orgId; }

    public String getRoleIds() { return roleIds; }
    public void setRoleIds(String roleIds) { this.roleIds = roleIds; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getMaxUses() { return maxUses; }
    public void setMaxUses(Integer maxUses) { this.maxUses = maxUses; }

    public Integer getUsedCount() { return usedCount; }
    public void setUsedCount(Integer usedCount) { this.usedCount = usedCount; }

    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }

    public Long getInvitedBy() { return invitedBy; }
    public void setInvitedBy(Long invitedBy) { this.invitedBy = invitedBy; }

    public Long getAcceptedBy() { return acceptedBy; }
    public void setAcceptedBy(Long acceptedBy) { this.acceptedBy = acceptedBy; }

    public LocalDateTime getAcceptedAt() { return acceptedAt; }
    public void setAcceptedAt(LocalDateTime acceptedAt) { this.acceptedAt = acceptedAt; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public Integer getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Integer deletedAt) { this.deletedAt = deletedAt; }

    /**
     * 判断是否已过期。注意：这里只做时间判断，不落库；
     * 列表展示时统一以本方法结果覆盖 status，避免依赖定时任务刷状态。
     */
    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(LocalDateTime.now());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SysInvitation that = (SysInvitation) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
