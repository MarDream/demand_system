package com.demand.system.module.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 个人设置-修改本人联系方式。姓名/账号/组织/角色等均不可通过此接口修改。
 */
public class UpdateProfileRequest {

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;

    /** 可选；填写时要求至少 3 位，允许数字/加号/连字符/空格。 */
    @Pattern(regexp = "^[0-9+\\-\\s]{3,20}$", message = "手机号格式不正确")
    private String phone;

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
}
