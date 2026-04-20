package com.demand.system.module.user.dto;

import lombok.Data;

@Data
public class UserQueryDTO {

    private String username;

    private String realName;

    private String status;

    private Long regionId;

    private Long departmentId;

    private Integer pageNum = 1;

    private Integer pageSize = 10;
}
