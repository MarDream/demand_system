package com.demand.system.module.bitable.service;

import com.demand.system.module.bitable.dto.BitableLeafMoveRequest;
import com.demand.system.module.bitable.dto.BitableLeafOrderItemDTO;

import java.util.List;

/**
 * 目录树叶子排序与移动（数据表 + 仪表盘共用同一序列）。
 */
public interface BitableLeafSortService {

    /**
     * 按传入顺序回写叶子节点的 sort_order（下标即排序号）。
     *
     * @param baseId   目标 Base（用于权限校验；调用方需保证列表内节点都属于该 Base 所在层级）
     * @param ordered  有序的叶子引用列表（数据表与仪表盘可混排）
     * @param userId   当前用户
     */
    void sortLeaves(Long baseId, List<BitableLeafOrderItemDTO> ordered, Long userId);

    /**
     * 把单个叶子（数据表 / 仪表盘）移动到目标 Base 分组。
     *
     * <p>只改该叶子自己的归属，不动同 Base 下的其它叶子；
     * 移动后叶子会排到目标层级的末尾。</p>
     *
     * @param request       kind=table/dashboard、id、targetGroupId（null=根层级）
     * @param userId        当前用户
     */
    void moveLeaf(BitableLeafMoveRequest request, Long userId);

    /**
     * 取某 Base 所在目录树层级的下一个排序号（= 同层级叶子最大 sort_order + 1）。
     *
     * <p>新建数据表/仪表盘时用它兜底，否则新节点 sort_order 默认 0 会插到列表最前面。</p>
     */
    int nextSortOrder(Long baseId);
}
