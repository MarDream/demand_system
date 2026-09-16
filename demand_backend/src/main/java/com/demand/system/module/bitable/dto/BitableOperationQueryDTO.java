package com.demand.system.module.bitable.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 多维表格-操作历史查询参数
 */
public class BitableOperationQueryDTO {

    private Integer pageNum;

    private Integer pageSize;

    /** 操作类型编码列表（OperationType.code），为空查全部 */
    private List<String> operationTypes;

    /** 操作人ID，为空查全部 */
    private Long userId;

    /** 起始时间（含），为空不限 */
    private LocalDateTime startTime;

    /** 截止时间（含），为空不限 */
    private LocalDateTime endTime;

    public Integer getPageNum() {
        return pageNum;
    }

    public void setPageNum(Integer pageNum) {
        this.pageNum = pageNum;
    }

    public Integer getPageSize() {
        return pageSize;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }

    public List<String> getOperationTypes() {
        return operationTypes;
    }

    public void setOperationTypes(List<String> operationTypes) {
        this.operationTypes = operationTypes;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }
}
