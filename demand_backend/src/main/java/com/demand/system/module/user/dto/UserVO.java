package com.demand.system.module.user.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserVO {

    private Long id;

    private String username;

    private String realName;

    private String email;

    private String phone;

    private String avatar;

    private String status;

    private String regionName;

    private String departmentName;

    private String positionName;

    private String systemRole;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
