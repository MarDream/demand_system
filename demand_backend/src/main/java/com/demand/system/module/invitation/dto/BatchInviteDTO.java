package com.demand.system.module.invitation.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 批量邀请入参：一次提交多个被邀请人，共用同一套组织/角色/有效期。
 */
public class BatchInviteDTO {

    /** 预分配组织ID */
    private Long orgId;

    /** 预分配角色ID */
    private List<Long> roleIds;

    /** 有效期天数；null 或 <=0 时使用默认 7 天 */
    private Integer expireDays;

    private String remark;

    @NotEmpty(message = "请至少填写一位被邀请人")
    @Size(max = 200, message = "单次批量邀请最多 200 人")
    private List<Member> members;

    public Long getOrgId() { return orgId; }
    public void setOrgId(Long orgId) { this.orgId = orgId; }

    public List<Long> getRoleIds() { return roleIds; }
    public void setRoleIds(List<Long> roleIds) { this.roleIds = roleIds; }

    public Integer getExpireDays() { return expireDays; }
    public void setExpireDays(Integer expireDays) { this.expireDays = expireDays; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public List<Member> getMembers() { return members; }
    public void setMembers(List<Member> members) { this.members = members; }

    /**
     * 单个被邀请人。手机号与邮箱至少填一个，两者都填时以手机号作为 target。
     */
    public static class Member {

        private String name;

        private String phone;

        private String email;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
    }
}
