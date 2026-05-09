package com.demand.system.module.rbac.dto;

import com.demand.system.module.rbac.entity.Role;
import lombok.Data;

@Data
public class RoleVO {
    private Long id;
    private String code;
    private String name;
    private String description;
    private Integer isSystem;

    public static RoleVO from(Role role) {
        RoleVO vo = new RoleVO();
        vo.setId(role.getId());
        vo.setCode(role.getCode());
        vo.setName(role.getName());
        vo.setDescription(role.getDescription());
        vo.setIsSystem(role.getIsSystem());
        return vo;
    }
}