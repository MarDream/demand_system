package com.demand.system.module.rbac.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.demand.system.module.rbac.entity.RoleGroupRelation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RoleGroupRelationMapper extends BaseMapper<RoleGroupRelation> {

    void deleteByRoleId(@Param("roleId") Long roleId);

    void deleteByRoleGroupId(@Param("roleGroupId") Long roleGroupId);
}