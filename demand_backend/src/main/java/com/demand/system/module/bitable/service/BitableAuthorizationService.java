package com.demand.system.module.bitable.service;

import com.demand.system.module.bitable.constant.MemberRole;

/**
 * 多维表格权限校验 Service
 * 基于 Base 成员角色进行权限控制：
 * - VIEWER: 只读
 * - COMMENTER: 只读 + 评论
 * - EDITOR: 读写记录，不能管理结构
 * - ADMIN: 读写 + 管理结构（字段/视图/成员），不能删除 Base 和移除 Owner
 * - OWNER: 全部权限
 */
public interface BitableAuthorizationService {

    /**
     * 获取用户在指定 Base 中的角色
     *
     * @param baseId 多维表格容器ID
     * @param userId 用户ID
     * @return 用户角色，如果不是成员则返回 null
     */
    MemberRole getMemberRole(Long baseId, Long userId);

    /**
     * 检查用户是否有指定 Base 的最低角色权限
     *
     * @param baseId        多维表格容器ID
     * @param userId        用户ID
     * @param requiredRole  最低需要的角色
     * @throws com.demand.system.common.exception.BusinessException 如果权限不足
     */
    void checkPermission(Long baseId, Long userId, MemberRole requiredRole);

    /**
     * 检查用户是否有指定 Base 的管理权限（ADMIN 及以上）
     *
     * @param baseId 多维表格容器ID
     * @param userId 用户ID
     * @throws com.demand.system.common.exception.BusinessException 如果权限不足
     */
    void checkManagePermission(Long baseId, Long userId);

    /**
     * 检查用户是否有指定 Base 的写入权限（EDITOR 及以上）
     *
     * @param baseId 多维表格容器ID
     * @param userId 用户ID
     * @throws com.demand.system.common.exception.BusinessException 如果权限不足
     */
    void checkWritePermission(Long baseId, Long userId);

    /**
     * 检查用户是否有指定 Base 的读取权限（VIEWER 及以上）
     *
     * @param baseId 多维表格容器ID
     * @param userId 用户ID
     * @throws com.demand.system.common.exception.BusinessException 如果权限不足
     */
    void checkReadPermission(Long baseId, Long userId);

    /**
     * 检查用户是否为 Base 的 Owner
     *
     * @param baseId 多维表格容器ID
     * @param userId 用户ID
     * @throws com.demand.system.common.exception.BusinessException 如果不是 Owner
     */
    void checkOwnerPermission(Long baseId, Long userId);

    /**
     * 从 tableId 反查 baseId
     *
     * @param tableId 数据表ID
     * @return 多维表格容器ID
     */
    Long getBaseIdByTableId(Long tableId);

    /**
     * 从 fieldId 反查 baseId
     *
     * @param fieldId 字段ID
     * @return 多维表格容器ID
     */
    Long getBaseIdByFieldId(Long fieldId);

    /**
     * 从 recordId 反查 baseId
     *
     * @param recordId 记录ID
     * @return 多维表格容器ID
     */
    Long getBaseIdByRecordId(Long recordId);

    /**
     * 从 viewId 反查 baseId
     *
     * @param viewId 视图ID
     * @return 多维表格容器ID
     */
    Long getBaseIdByViewId(Long viewId);

    /**
     * 从 commentId 反查 baseId
     *
     * @param commentId 评论ID
     * @return 多维表格容器ID
     */
    Long getBaseIdByCommentId(Long commentId);

    /**
     * 清除指定 Base 的角色缓存
     *
     * @param baseId 多维表格容器ID
     */
    void clearRoleCache(Long baseId);

    /**
     * 清除指定 Base 中指定用户的角色缓存
     *
     * @param baseId 多维表格容器ID
     * @param userId 用户ID
     */
    void clearRoleCache(Long baseId, Long userId);
}
