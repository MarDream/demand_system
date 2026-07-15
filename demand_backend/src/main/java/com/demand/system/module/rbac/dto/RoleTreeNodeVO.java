package com.demand.system.module.rbac.dto;

import java.util.List;

/**
 * 角色树节点 VO
 * 用于工作流等场景的角色选择器，以分组树形式展示角色
 */
public class RoleTreeNodeVO {

    /** 分组ID */
    private Long groupId;

    /** 分组名称 */
    private String groupName;

    /** 是否默认分组 */
    private Integer isDefault;

    /** 分组下的角色列表 */
    private List<RoleItemVO> children;

    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public Integer getIsDefault() {
        return isDefault;
    }

    public void setIsDefault(Integer isDefault) {
        this.isDefault = isDefault;
    }

    public List<RoleItemVO> getChildren() {
        return children;
    }

    public void setChildren(List<RoleItemVO> children) {
        this.children = children;
    }

    /**
     * 角色项 VO
     */
    public static class RoleItemVO {
        private Long id;
        private String name;
        private String code;
        private Integer isDefault;
        private List<Long> groupIds;

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

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public Integer getIsDefault() {
            return isDefault;
        }

        public void setIsDefault(Integer isDefault) {
            this.isDefault = isDefault;
        }

        public List<Long> getGroupIds() {
            return groupIds;
        }

        public void setGroupIds(List<Long> groupIds) {
            this.groupIds = groupIds;
        }
    }
}