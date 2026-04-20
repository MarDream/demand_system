package com.demand.system.module.user.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UserUpdateDTO {

    @NotNull(message = "用户ID不能为空")
    private Long id;

    private String realName;

    private String email;

    private String phone;

    private String avatar;

    private String status;
}
