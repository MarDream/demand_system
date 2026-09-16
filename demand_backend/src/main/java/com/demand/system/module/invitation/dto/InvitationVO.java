package com.demand.system.module.invitation.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 邀请记录视图对象
 */
public class InvitationVO {

    private Long id;

    private String inviteCode;

    /** link / batch */
    private String inviteType;

    private String inviteTypeText;

    private String target;

    private String targetName;

    private Long orgId;

    private String orgName;

    private List<Long> roleIds;

    private List<String> roleNames;

    /** pending / accepted / expired / revoked */
    private String status;

    private String statusText;

    private Integer maxUses;

    private Integer usedCount;

    private LocalDateTime expiresAt;

    private Long invitedBy;

    private String inviterName;

    private Long acceptedBy;

    private LocalDateTime acceptedAt;

    private String remark;

    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getInviteCode() { return inviteCode; }
    public void setInviteCode(String inviteCode) { this.inviteCode = inviteCode; }

    public String getInviteType() { return inviteType; }
    public void setInviteType(String inviteType) { this.inviteType = inviteType; }

    public String getInviteTypeText() { return inviteTypeText; }
    public void setInviteTypeText(String inviteTypeText) { this.inviteTypeText = inviteTypeText; }

    public String getTarget() { return target; }
    public void setTarget(String target) { this.target = target; }

    public String getTargetName() { return targetName; }
    public void setTargetName(String targetName) { this.targetName = targetName; }

    public Long getOrgId() { return orgId; }
    public void setOrgId(Long orgId) { this.orgId = orgId; }

    public String getOrgName() { return orgName; }
    public void setOrgName(String orgName) { this.orgName = orgName; }

    public List<Long> getRoleIds() { return roleIds; }
    public void setRoleIds(List<Long> roleIds) { this.roleIds = roleIds; }

    public List<String> getRoleNames() { return roleNames; }
    public void setRoleNames(List<String> roleNames) { this.roleNames = roleNames; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getStatusText() { return statusText; }
    public void setStatusText(String statusText) { this.statusText = statusText; }

    public Integer getMaxUses() { return maxUses; }
    public void setMaxUses(Integer maxUses) { this.maxUses = maxUses; }

    public Integer getUsedCount() { return usedCount; }
    public void setUsedCount(Integer usedCount) { this.usedCount = usedCount; }

    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }

    public Long getInvitedBy() { return invitedBy; }
    public void setInvitedBy(Long invitedBy) { this.invitedBy = invitedBy; }

    public String getInviterName() { return inviterName; }
    public void setInviterName(String inviterName) { this.inviterName = inviterName; }

    public Long getAcceptedBy() { return acceptedBy; }
    public void setAcceptedBy(Long acceptedBy) { this.acceptedBy = acceptedBy; }

    public LocalDateTime getAcceptedAt() { return acceptedAt; }
    public void setAcceptedAt(LocalDateTime acceptedAt) { this.acceptedAt = acceptedAt; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
