package com.demand.system.module.rbac.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.demand.system.module.rbac.entity.RoleDataScopeOrg;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 角色数据权限-可见组织范围 Mapper
 */
public interface RoleDataScopeOrgMapper extends BaseMapper<RoleDataScopeOrg> {

    /**
     * 根据角色ID列表查询所有关联的组织ID
     *
     * @param roleIds 角色ID列表
     * @return 组织ID列表
     */
    List<Long> selectOrgIdsByRoleIds(@Param("roleIds") List<Long> roleIds);

    /**
     * 删除角色下所有数据权限组织
     *
     * @param roleId 角色ID
     * @return 删除行数
     */
    int deleteByRoleId(@Param("roleId") Long roleId);
}
