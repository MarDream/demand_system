package com.demand.system.module.hr.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.Map;

public class HrRecordCreateDTO {

    @NotBlank(message = "事件类型不能为空")
    private String recordType;

    @NotNull(message = "请选择员工")
    private Long userId;

    @NotBlank(message = "事项标题不能为空")
    private String title;

    /** 类型化明细：入职(部门/岗位)、异动(原/新部门等)、合同(编号/期限)等 */
    private Map<String, Object> detail;

    /** 业务日期 */
    private LocalDate recordDate;

    /** 状态：processing(默认)/done/cancelled */
    private String status;

    public String getRecordType() {
        return recordType;
    }

    public void setRecordType(String recordType) {
        this.recordType = recordType;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Map<String, Object> getDetail() {
        return detail;
    }

    public void setDetail(Map<String, Object> detail) {
        this.detail = detail;
    }

    public LocalDate getRecordDate() {
        return recordDate;
    }

    public void setRecordDate(LocalDate recordDate) {
        this.recordDate = recordDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
