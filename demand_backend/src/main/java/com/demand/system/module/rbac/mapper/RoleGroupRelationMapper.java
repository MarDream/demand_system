package com.demand.system.module.rbac.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.demand.system.module.rbac.entity.RoleGroupRelation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RoleGroupRelationMapper extends BaseMapper<RoleGroupRelation> {

    /**
     * 删除角色下的所有关联分组
     */
    void deleteByRoleId(@Param("roleId") Long roleId);

    /**
     * 删除角色组下的所有关联
     */
    void deleteByRoleGroupId(@Param("roleGroupId") Long roleGroupId);
}