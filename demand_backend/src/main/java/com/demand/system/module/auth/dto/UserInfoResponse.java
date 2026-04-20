package com.demand.system.module.auth.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class UserInfoResponse {

    private Long id;

    private String username;

    private String realName;

    private String email;

    private String avatar;

    private List<String> roles;

    private Long regionId;

    private Long departmentId;

    private Long positionId;
}
