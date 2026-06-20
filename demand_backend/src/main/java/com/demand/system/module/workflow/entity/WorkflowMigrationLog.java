package com.demand.system.module.workflow.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 工作流版本迁移日志
 */
@TableName("workflow_migration_logs")
public class WorkflowMigrationLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long fromVersionId;

    private Long toVersionId;

    private Long requirementId;

    private String migrationType;

    private String migrationStatus;

    private String errorMessage;

    private Long operatorId;

    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getFromVersionId() {
        return fromVersionId;
    }

    public void setFromVersionId(Long fromVersionId) {
        this.fromVersionId = fromVersionId;
    }

    public Long getToVersionId() {
        return toVersionId;
    }

    public void setToVersionId(Long toVersionId) {
        this.toVersionId = toVersionId;
    }

    public Long getRequirementId() {
        return requirementId;
    }

    public void setRequirementId(Long requirementId) {
        this.requirementId = requirementId;
    }

    public String getMigrationType() {
        return migrationType;
    }

    public void setMigrationType(String migrationType) {
        this.migrationType = migrationType;
    }

    public String getMigrationStatus() {
        return migrationStatus;
    }

    public void setMigrationStatus(String migrationStatus) {
        this.migrationStatus = migrationStatus;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WorkflowMigrationLog that = (WorkflowMigrationLog) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
