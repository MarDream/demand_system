package com.demand.system.module.rbac.dto;

import com.demand.system.module.rbac.entity.RoleGroup;

public class RoleGroupVO {

    private Long id;
    private String name;
    private String description;
    private Integer sortOrder;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public static RoleGroupVO from(RoleGroup roleGroup) {
        RoleGroupVO vo = new RoleGroupVO();
        vo.setId(roleGroup.getId());
        vo.setName(roleGroup.getName());
        vo.setDescription(roleGroup.getDescription());
        vo.setSortOrder(roleGroup.getSortOrder());
        return vo;
    }
}
