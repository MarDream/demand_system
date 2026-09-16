package com.demand.system.module.bitable.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 字段权限批量保存请求体。
 * <p>
 * 前端字段权限面板一次性提交「本次改动过的」条目；未出现在列表中的 (角色, 字段) 组合保持原样。
 */
public class BitableFieldPermissionBatchDTO {

    @NotNull(message = "Base ID不能为空")
    private Long baseId;

    private List<BitableFieldPermissionDTO> changes;

    public Long getBaseId() { return baseId; }
    public void setBaseId(Long baseId) { this.baseId = baseId; }

    public List<BitableFieldPermissionDTO> getChanges() { return changes; }
    public void setChanges(List<BitableFieldPermissionDTO> changes) { this.changes = changes; }
}
