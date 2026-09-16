package com.demand.system.module.invitation.dto;

import java.time.LocalDateTime;

/**
 * 邀请链接的对外可见信息（匿名接口返回，不暴露内部字段）
 */
public class InviteInfoVO {

    private String inviteCode;

    private String inviteType;

    /** 邀请人姓名，用于页面上「XX 邀请你加入」 */
    private String inviterName;

    private String orgName;

    private LocalDateTime expiresAt;

    /** 链接当前是否可用 */
    private boolean valid;

    /** 不可用时的原因，可直接展示给用户 */
    private String invalidReason;

    /** 批量邀请时预填的姓名，可空 */
    private String targetName;

    public String getInviteCode() { return inviteCode; }
    public void setInviteCode(String inviteCode) { this.inviteCode = inviteCode; }

    public String getInviteType() { return inviteType; }
    public void setInviteType(String inviteType) { this.inviteType = inviteType; }

    public String getInviterName() { return inviterName; }
    public void setInviterName(String inviterName) { this.inviterName = inviterName; }

    public String getOrgName() { return orgName; }
    public void setOrgName(String orgName) { this.orgName = orgName; }

    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }

    public boolean isValid() { return valid; }
    public void setValid(boolean valid) { this.valid = valid; }

    public String getInvalidReason() { return invalidReason; }
    public void setInvalidReason(String invalidReason) { this.invalidReason = invalidReason; }

    public String getTargetName() { return targetName; }
    public void setTargetName(String targetName) { this.targetName = targetName; }
}
