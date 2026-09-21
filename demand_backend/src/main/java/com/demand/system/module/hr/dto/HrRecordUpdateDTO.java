package com.demand.system.module.hr.dto;

import java.time.LocalDate;
import java.util.Map;

public class HrRecordUpdateDTO {

    private String title;

    private Map<String, Object> detail;

    private LocalDate recordDate;

    /** 状态：processing/done/cancelled（置为 done 时触发员工状态联动） */
    private String status;

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
