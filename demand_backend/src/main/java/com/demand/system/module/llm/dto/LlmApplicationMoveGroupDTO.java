package com.demand.system.module.llm.dto;

/**
 * LLM 功能点-归组DTO
 * <p>
 * {@code groupId} 为 null 表示移出分组（未分组）。
 */
public class LlmApplicationMoveGroupDTO {

    /**
     * 目标分组ID，null=未分组
     */
    private Long groupId;

    public Long getGroupId() { return groupId; }
    public void setGroupId(Long groupId) { this.groupId = groupId; }
}
