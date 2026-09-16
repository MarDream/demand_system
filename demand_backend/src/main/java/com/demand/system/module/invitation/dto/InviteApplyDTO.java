package com.demand.system.module.invitation.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 被邀请人通过邀请链接提交的资料（匿名接口，需严格校验）
 */
public class InviteApplyDTO {

    @NotBlank(message = "请填写姓名")
    @Size(max = 64, message = "姓名过长")
    private String name;

    @NotBlank(message = "请填写手机号")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @NotBlank(message = "请填写邮箱")
    @Email(message = "邮箱格式不正确")
    private String email;

    @Size(max = 255, message = "留言过长")
    private String remark;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
