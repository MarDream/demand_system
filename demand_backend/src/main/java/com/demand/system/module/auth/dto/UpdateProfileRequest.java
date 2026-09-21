package com.demand.system.module.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 个人设置-修改本人联系方式与头像。姓名/账号/组织/角色等均不可通过此接口修改。
 */
public class UpdateProfileRequest {

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;

    /** 可选；填写时要求至少 3 位，允许数字/加号/连字符/空格。 */
    @Pattern(regexp = "^[0-9+\\-\\s]{3,20}$", message = "手机号格式不正确")
    private String phone;

    /**
     * 可选；头像地址。两种来源：
     * 1. 预设头像标识（preset:xxx 前缀，前端内置 SVG）
     * 2. 上传文件后返回的 URL（/api/v1/files/{id}/preview 或完整链接）
     * 传空字符串表示清除头像（回退到姓名首字占位）。
     */
    @Size(max = 512, message = "头像地址过长")
    private String avatar;

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }
}
