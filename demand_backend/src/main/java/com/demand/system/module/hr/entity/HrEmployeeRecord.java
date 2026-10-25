package com.demand.system.module.hr.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

@TableName(value = "hr_employee_records", autoResultMap = true)
public class HrEmployeeRecord {

    public static final String TYPE_ONBOARDING = "onboarding";
    /** 事件类型：新人成长 */
    public static final String TYPE_NEWCOMER = "newcomer";
    /** 事件类型：转正 */
    public static final String TYPE_REGULARIZATION = "regularization";
    public static final String TYPE_TRANSFER = "transfer";
    public static final String TYPE_RESIGNATION = "resignation";
    public static final String TYPE_CONTRACT = "contract";
    public static final String TYPE_RETIREMENT = "retirement";
    /** 事件类型：员工关怀 */
    public static final String TYPE_CARE = "care";
    /** 事件类型：用工安全 */
    public static final String TYPE_SAFETY = "safety";

    public static final String STATUS_PROCESSING = "processing";
    public static final String STATUS_DONE = "done";
    public static final String STATUS_CANCELLED = "cancelled";

    @TableId(type = IdType.AUTO)
    private Long id;

    private String recordType;

    private Long userId;

    private String title;

    /** 类型化明细(JSON)：各模块字段见前端表单 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> detail;

    /** 业务日期(生效日/到期日/关怀日等) */
    private LocalDate recordDate;

    private String status;

    private Long operatorId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deletedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    public Long getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(Long operatorId) {
        this.operatorId = operatorId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Integer getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Integer deletedAt) {
        this.deletedAt = deletedAt;
    }
}
