package com.demand.system.module.bitable.service;

import com.demand.system.module.bitable.dto.BitableTableGroupCreateDTO;
import com.demand.system.module.bitable.dto.BitableTableGroupMoveDTO;
import com.demand.system.module.bitable.dto.BitableTableGroupUpdateDTO;
import com.demand.system.module.bitable.dto.BitableTableGroupVO;

import java.util.List;

/**
 * 多维表格-数据表分组 Service
 * <p>
 * 分组通过 {@code parentId} 自关联形成任意层级的目录树。
 * 树上的「全部实体」「未分组」为前端虚拟节点，不落库。
 */
public interface BitableTableGroupService {

    /**
     * 查询指定 Base 的分组树（含各节点数据表数量）
     *
     * @param baseId 多维表格容器ID
     * @return 根分组列表，children 递归嵌套
     */
    List<BitableTableGroupVO> listGroupTree(Long baseId);

    /**
     * 创建分组
     *
     * @param baseId Base ID
     * @param dto    创建参数（name、parentId）
     * @param userId 创建人ID
     * @return 新分组ID
     */
    Long createGroup(Long baseId, BitableTableGroupCreateDTO dto, Long userId);

    /**
     * 重命名分组
     *
     * @param id     分组ID
     * @param dto    更新参数
     * @param userId 操作人ID
     */
    void renameGroup(Long id, BitableTableGroupUpdateDTO dto, Long userId);

    /**
     * 移动分组（变更父级 / 同级排序），服务端做防环校验
     *
     * @param id     分组ID
     * @param dto    移动参数
     * @param userId 操作人ID
     */
    void moveGroup(Long id, BitableTableGroupMoveDTO dto, Long userId);

    /**
     * 删除分组：子分组与数据表上移到父级，不级联删除
     *
     * @param id     分组ID
     * @param userId 操作人ID
     */
    void deleteGroup(Long id, Long userId);

    /**
     * 数据表归组
     *
     * @param tableId 数据表ID
     * @param groupId 目标分组ID，null=移出分组（未分组）
     * @param userId  操作人ID
     */
    void moveTableToGroup(Long tableId, Long groupId, Long userId);

    /**
     * 批量排序数据表分组：orderedIds 中「同级」分组的排列顺序即目标顺序。
     * 服务端会按各分组当前的 parentId 分桶校验同级一致性，避免把不同层级的分组混排。
     *
     * @param orderedIds 按目标顺序排列的分组ID列表（须为同一父级下的同级分组，可乱序传入但须覆盖完整同级集合）
     * @param userId     操作人ID
     */
    void sortGroups(List<Long> orderedIds, Long userId);

    /**
     * 从 groupId 反查 baseId
     *
     * @param groupId 分组ID
     * @return 多维表格容器ID
     */
    Long getBaseIdByGroupId(Long groupId);
}
