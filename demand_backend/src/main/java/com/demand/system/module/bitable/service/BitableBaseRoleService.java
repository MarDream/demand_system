package com.demand.system.module.bitable.service;

import com.demand.system.module.bitable.dto.*;

import java.util.List;

public interface BitableBaseRoleService {

    /**
     * 获取 Base 下所有角色（系统角色 + 自定义角色）及其权限配置
     */
    List<BitableBaseRoleVO> listRolesWithPermissions(Long baseId, String permissionType);

    /**
     * 创建自定义角色
     */
    BitableBaseCustomRoleCreateDTO createCustomRole(BitableBaseCustomRoleCreateDTO dto, Long userId);

    /**
     * 更新自定义角色
     */
    void updateCustomRole(Long roleId, BitableBaseCustomRoleUpdateDTO dto, Long userId);

    /**
     * 删除自定义角色（同时清理成员和权限）
     */
    void deleteCustomRole(Long roleId, Long userId);

    /**
     * 为自定义角色添加成员
     */
    void addRoleMember(BitableBaseCustomRoleMemberDTO dto, Long userId);

    /**
     * 移除自定义角色成员
     */
    void removeRoleMember(Long roleId, String memberType, Long memberId, Long userId);

    /**
     * 设置角色对数据表的权限
     */
    void setRolePermission(BitableBaseRolePermissionDTO dto, Long userId);

    /**
     * 批量设置权限
     */
    void batchSetRolePermissions(Long baseId, String roleType, String systemRoleCode, Long customRoleId,
                                  String permissionType, String permissionLevel, Long userId);

    /**
     * 批量保存多条权限变更（事务内一次性提交）
     */
    void batchSaveRolePermissions(Long baseId, List<BitableBaseRolePermissionDTO> changes, Long userId);
}
