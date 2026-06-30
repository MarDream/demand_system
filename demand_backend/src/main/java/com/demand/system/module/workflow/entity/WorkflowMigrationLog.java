package com.demand.system.module.workflow.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 工作流版本迁移日志
 *
 * ADR-002 扩展（2026-06-30）：
 * - 增加 fromNodeId/toNodeId/fromNodeName/toNodeName — 记录节点映射
 * - 增加 nodeMappingJson — 记录完整映射表
 * - 增加 instanceId — 直接关联实例
 * - 增加 planId — 关联迁移计划
 */
@TableName("workflow_migration_logs")
public class WorkflowMigrationLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long fromVersionId;

    private Long toVersionId;

    private String fromNodeId;

    private String toNodeId;

    private String fromNodeName;

    private String toNodeName;

    @TableField(typeHandler = com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler.class)
    private List<WorkflowMigrationPlan.NodeMappingItem> nodeMappingJson;

    private Long requirementId;

    private Long instanceId;

    private Long planId;

    private String migrationType;

    private String migrationStatus;

    private String errorMessage;

    private Long operatorId;

    private LocalDateTime createdAt;

    // ============ Getters & Setters ============

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getFromVersionId() { return fromVersionId; }
    public void setFromVersionId(Long fromVersionId) { this.fromVersionId = fromVersionId; }

    public Long getToVersionId() { return toVersionId; }
    public void setToVersionId(Long toVersionId) { this.toVersionId = toVersionId; }

    public String getFromNodeId() { return fromNodeId; }
    public void setFromNodeId(String fromNodeId) { this.fromNodeId = fromNodeId; }

    public String getToNodeId() { return toNodeId; }
    public void setToNodeId(String toNodeId) { this.toNodeId = toNodeId; }

    public String getFromNodeName() { return fromNodeName; }
    public void setFromNodeName(String fromNodeName) { this.fromNodeName = fromNodeName; }

    public String getToNodeName() { return toNodeName; }
    public void setToNodeName(String toNodeName) { this.toNodeName = toNodeName; }

    public List<WorkflowMigrationPlan.NodeMappingItem> getNodeMappingJson() { return nodeMappingJson; }
    public void setNodeMappingJson(List<WorkflowMigrationPlan.NodeMappingItem> nodeMappingJson) { this.nodeMappingJson = nodeMappingJson; }

    public Long getRequirementId() { return requirementId; }
    public void setRequirementId(Long requirementId) { this.requirementId = requirementId; }

    public Long getInstanceId() { return instanceId; }
    public void setInstanceId(Long instanceId) { this.instanceId = instanceId; }

    public Long getPlanId() { return planId; }
    public void setPlanId(Long planId) { this.planId = planId; }

    public String getMigrationType() { return migrationType; }
    public void setMigrationType(String migrationType) { this.migrationType = migrationType; }

    public String getMigrationStatus() { return migrationStatus; }
    public void setMigrationStatus(String migrationStatus) { this.migrationStatus = migrationStatus; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public Long getOperatorId() { return operatorId; }
    public void setOperatorId(Long operatorId) { this.operatorId = operatorId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WorkflowMigrationLog that = (WorkflowMigrationLog) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }
}
