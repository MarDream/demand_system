package com.demand.system.module.bitable.dto;

/**
 * 多维表格-数据表归组的DTO
 * <p>
 * {@code groupId} 为 null 表示移出分组（变为「未分组」）。
 */
public class BitableTableMoveGroupDTO {

    private Long groupId;

    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
    }
}
