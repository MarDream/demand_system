package com.demand.system.module.bitable.dto;

import java.util.List;

/**
 * 目录树叶子排序请求：{ baseId, leaves: [{kind, id}, ...] }
 *
 * <p>leaves 按目标顺序排列，下标即最终 sort_order。</p>
 */
public class BitableLeafSortRequest {

    /** 锚点 Base：用它的 group_id 判定「同一层级」 */
    private Long baseId;

    private List<BitableLeafOrderItemDTO> leaves;

    public Long getBaseId() {
        return baseId;
    }

    public void setBaseId(Long baseId) {
        this.baseId = baseId;
    }

    public List<BitableLeafOrderItemDTO> getLeaves() {
        return leaves;
    }

    public void setLeaves(List<BitableLeafOrderItemDTO> leaves) {
        this.leaves = leaves;
    }
}
