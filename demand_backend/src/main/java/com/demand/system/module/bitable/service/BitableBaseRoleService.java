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

    /**
     * 解析用户在某对象（tableId 列，permissionType 区分视图/仪表盘等）上的生效权限级别。
     * 未配置返回 null（由调用方决定默认值）；多角色命中时取最宽松；所有者恒为最宽松。
     */
    String resolveObjectPermission(Long baseId, Long tableId, String permissionType, Long userId);

    /**
     * 视图管理权限校验：角色视图权限被设为「可查看」时，禁止新增/修改/删除视图。
     * 未配置或完全权限放行；所有者恒放行。
     */
    void checkViewManagePermission(Long baseId, Long tableId, Long userId);

    /**
     * 仪表盘整体权限：full/view/none。未配置默认 full；所有者恒 full。
     */
    String resolveDashboardPermission(Long baseId, Long dashboardId, Long userId);

    /**
     * 仪表盘数据权限：full=基于全部数据统计（默认）/view=基于访问者权限统计（增值占位）/none=图表不可见。
     */
    String resolveDashboardDataPermission(Long baseId, Long dashboardId, Long userId);
}
