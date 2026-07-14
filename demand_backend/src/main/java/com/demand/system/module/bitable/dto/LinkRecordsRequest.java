package com.demand.system.module.bitable.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 创建关联记录请求体
 */
public class LinkRecordsRequest {

    @NotNull(message = "记录ID不能为空")
    private Long recordId;

    @NotNull(message = "目标记录ID列表不能为空")
    private List<Long> targetRecordIds;

    public Long getRecordId() {
        return recordId;
    }

    public void setRecordId(Long recordId) {
        this.recordId = recordId;
    }

    public List<Long> getTargetRecordIds() {
        return targetRecordIds;
    }

    public void setTargetRecordIds(List<Long> targetRecordIds) {
        this.targetRecordIds = targetRecordIds;
    }
}