package com.demand.system.module.bitable.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class BitableBaseCustomRoleMemberDTO {

    @NotNull(message = "角色ID不能为空")
    private Long roleId;

    @NotBlank(message = "成员类型不能为空")
    private String memberType;

    @NotNull(message = "成员ID不能为空")
    private Long memberId;

    public Long getRoleId() { return roleId; }
    public void setRoleId(Long roleId) { this.roleId = roleId; }

    public String getMemberType() { return memberType; }
    public void setMemberType(String memberType) { this.memberType = memberType; }

    public Long getMemberId() { return memberId; }
    public void setMemberId(Long memberId) { this.memberId = memberId; }
}
