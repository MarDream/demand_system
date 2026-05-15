package com.demand.system.module.user.dto;

import lombok.Data;

@Data
public class UserUpdateDTO {

    private Long id;

    private String realName;

    private String email;

    private String phone;

    private String avatar;

    private String status;

    private Long regionId;

    private Long departmentId;

    private Long orgId;

    private Long positionId;
}
