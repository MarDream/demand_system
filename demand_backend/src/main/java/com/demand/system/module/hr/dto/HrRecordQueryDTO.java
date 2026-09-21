package com.demand.system.module.hr.dto;

public class HrRecordQueryDTO {

    /** 事件类型：onboarding/newcomer/regularization/transfer/resignation/contract/retirement/care/safety */
    private String recordType;

    /** 状态：processing/done/cancelled */
    private String status;

    /** 关联员工 */
    private Long userId;

    private Integer pageNum = 1;

    private Integer pageSize = 20;

    public String getRecordType() {
        return recordType;
    }

    public void setRecordType(String recordType) {
        this.recordType = recordType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

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
}
