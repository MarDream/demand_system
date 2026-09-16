package com.demand.system.module.invitation.dto;

import java.util.List;

/**
 * 生成邀请链接的入参
 */
public class InvitationLinkCreateDTO {

    /** 预分配组织ID，可空（接受后由审批人再指定） */
    private Long orgId;

    /** 预分配角色ID，可空 */
    private List<Long> roleIds;

    /** 有效期天数；null 或 <=0 且 neverExpire=false 时使用默认 7 天 */
    private Integer expireDays;

    /** true = 永不过期 */
    private Boolean neverExpire;

    /** 链接最多可用次数，0 或不传 = 不限次 */
    private Integer maxUses;

    private String remark;

    public Long getOrgId() { return orgId; }
    public void setOrgId(Long orgId) { this.orgId = orgId; }

    public List<Long> getRoleIds() { return roleIds; }
    public void setRoleIds(List<Long> roleIds) { this.roleIds = roleIds; }

    public Integer getExpireDays() { return expireDays; }
    public void setExpireDays(Integer expireDays) { this.expireDays = expireDays; }

    public Boolean getNeverExpire() { return neverExpire; }
    public void setNeverExpire(Boolean neverExpire) { this.neverExpire = neverExpire; }

    public Integer getMaxUses() { return maxUses; }
    public void setMaxUses(Integer maxUses) { this.maxUses = maxUses; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
