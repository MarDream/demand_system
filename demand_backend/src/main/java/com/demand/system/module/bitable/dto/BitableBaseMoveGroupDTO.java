package com.demand.system.module.bitable.dto;

/**
 * 多维表格-Base 归组DTO（groupId 为 null 表示移出分组）
 */
public class BitableBaseMoveGroupDTO {

    /**
     * 目标分组ID，null=移出分组（未分组）
     */
    private Long groupId;

    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
    }
}
