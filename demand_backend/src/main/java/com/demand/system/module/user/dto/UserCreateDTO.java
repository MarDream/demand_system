package com.demand.system.module.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class UserCreateDTO {

    @NotBlank(message = "用户名不能为空")
    @Pattern(regexp = "^[a-zA-Z0-9_]{3,20}$", message = "用户名仅支持字母、数字、下划线，3-20位")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;

    private String realName;

    @Email(message = "邮箱格式不正确")
    private String email;

    private String phone;
}
