package com.demand.system.module.bitable.service;

import com.demand.system.module.bitable.dto.BitableBaseGroupCreateDTO;
import com.demand.system.module.bitable.dto.BitableBaseGroupMoveDTO;
import com.demand.system.module.bitable.dto.BitableBaseGroupUpdateDTO;
import com.demand.system.module.bitable.dto.BitableBaseGroupVO;

import java.util.List;

/**
 * 多维表格-Base分组 Service
 * <p>
 * Base 分组为全局维度的目录树，用于多维表格列表页左侧的树形管理。
 */
public interface BitableBaseGroupService {

    /**
     * 查询分组树
     */
    List<BitableBaseGroupVO> listGroupTree();

    /**
     * 新建分组
     *
     * @return 新分组ID
     */
    Long createGroup(BitableBaseGroupCreateDTO dto, Long userId);

    /**
     * 重命名分组
     */
    void renameGroup(Long id, BitableBaseGroupUpdateDTO dto, Long userId);

    /**
     * 移动分组（变更父级 / 同级排序）
     */
    void moveGroup(Long id, BitableBaseGroupMoveDTO dto, Long userId);

    /**
     * 删除分组（子分组与 Base 上移到父级，不级联删除）
     */
    void deleteGroup(Long id, Long userId);

    /**
     * Base 归组（groupId 为 null 表示移出分组）
     */
    void moveBaseToGroup(Long baseId, Long groupId, Long userId);
}
