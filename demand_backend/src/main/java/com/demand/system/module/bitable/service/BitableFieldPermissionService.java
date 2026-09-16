package com.demand.system.module.bitable.service;

import com.demand.system.module.bitable.dto.BitableFieldPermissionDTO;

import java.util.List;
import java.util.Map;

/**
 * 多维表格-字段级权限 Service。
 * <p>
 * 字段权限在「表级权限」之下再收窄一层：同一个角色在同一张表内，
 * 可以对不同字段分别设为 {@code editable} / {@code readonly} / {@code hidden}。
 * 未配置的字段默认 {@code editable}。
 */
public interface BitableFieldPermissionService {

    /**
     * 字段权限面板数据源：返回某张表已配置的非默认（非 editable）权限条目。
     *
     * @param baseId  多维表格容器ID
     * @param tableId 数据表ID，传 null 表示返回整个 Base 的配置
     * @param userId  当前用户ID（需 ADMIN 及以上）
     */
    List<BitableFieldPermissionDTO> listFieldPermissions(Long baseId, Long tableId, Long userId);

    /**
     * 批量保存字段权限。
     * <p>
     * 每条变更先按 (baseId, role, fieldId) 删除旧行再插入；{@code editable} 视为默认值，
     * 只删除不插入（保持表精简，也让「恢复默认」语义成立）。
     *
     * @param baseId  多维表格容器ID
     * @param changes 变更条目，可为空
     * @param userId  当前用户ID（需 ADMIN 及以上）
     */
    void batchSaveFieldPermissions(Long baseId, List<BitableFieldPermissionDTO> changes, Long userId);

    /**
     * 解析当前用户在指定表上的生效字段权限。
     * <p>
     * 用户可能同时拥有系统角色与多个自定义角色，取并集中最宽松的一档
     * （editable &gt; readonly &gt; hidden）；未配置的字段不出现在结果里（即默认 editable）。
     *
     * @return {@code Map<fieldId, permissionLevel>}，仅含 readonly / hidden 的字段
     */
    Map<Long, String> resolveFieldPermissions(Long tableId, Long userId);

    /**
     * 校验用户对指定字段是否有编辑权限，无权限时抛 {@code BusinessException}。
     * <p>
     * 写入路径（新建记录 / 更新记录 / 单元格编辑 / 导入）都应调用，避免只读或隐藏字段被绕过前端改写。
     */
    void checkFieldEditable(Long fieldId, Long userId);

    /**
     * 批量校验用户对一组字段的编辑权限（一次解析，避免按单元格逐个查库）。
     *
     * @param tableId  数据表ID
     * @param fieldIds 待写入的字段ID集合
     * @param userId   当前用户ID
     * @throws com.demand.system.common.exception.BusinessException 命中只读或隐藏字段时抛出
     */
    void checkFieldsEditable(Long tableId, java.util.Collection<Long> fieldIds, Long userId);

    /**
     * 删除某个字段的全部字段权限配置（删除字段时调用）。
     * <p>
     * {@code bitable_field_permissions} 上没有外键，删字段不会级联；
     * 不清理就会留下指向已删字段的孤儿行。
     */
    void deleteByFieldId(Long fieldId);

    /** 删除某张表下全部字段权限配置（删除数据表时调用） */
    void deleteByTableId(Long tableId);
}
