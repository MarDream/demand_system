package com.demand.system.module.workflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 工作流版本迁移计划（ADR-002）
 *
 * 管理员创建迁移计划后，配置旧节点→新节点的映射，
 * 预检影响后手动执行，逐条迁移运行中的工作流实例。
 */
@TableName(value = "workflow_migration_plans", autoResultMap = true)
public class WorkflowMigrationPlan {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long fromVersionId;

    private Long toVersionId;

    private Long projectId;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<NodeMappingItem> nodeMapping;

    private String status;

    private Integer totalInstanceCount;

    private Integer migratedCount;

    private Integer failedCount;

    private Long operatorId;

    private LocalDateTime startedAt;

    private LocalDateTime completedAt;

    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    // ============ 节点映射内部类 ============

    public static class NodeMappingItem {
        private String fromNodeId;
        private String toNodeId;
        private String fromNodeName;
        private String toNodeName;

        public NodeMappingItem() {}

        public NodeMappingItem(String fromNodeId, String toNodeId, String fromNodeName, String toNodeName) {
            this.fromNodeId = fromNodeId;
            this.toNodeId = toNodeId;
            this.fromNodeName = fromNodeName;
            this.toNodeName = toNodeName;
        }

        public String getFromNodeId() { return fromNodeId; }
        public void setFromNodeId(String fromNodeId) { this.fromNodeId = fromNodeId; }
        public String getToNodeId() { return toNodeId; }
        public void setToNodeId(String toNodeId) { this.toNodeId = toNodeId; }
        public String getFromNodeName() { return fromNodeName; }
        public void setFromNodeName(String fromNodeName) { this.fromNodeName = fromNodeName; }
        public String getToNodeName() { return toNodeName; }
        public void setToNodeName(String toNodeName) { this.toNodeName = toNodeName; }
    }

    // ============ Getters & Setters ============

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getFromVersionId() { return fromVersionId; }
    public void setFromVersionId(Long fromVersionId) { this.fromVersionId = fromVersionId; }

    public Long getToVersionId() { return toVersionId; }
    public void setToVersionId(Long toVersionId) { this.toVersionId = toVersionId; }

    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }

    public List<NodeMappingItem> getNodeMapping() { return nodeMapping; }
    public void setNodeMapping(List<NodeMappingItem> nodeMapping) { this.nodeMapping = nodeMapping; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getTotalInstanceCount() { return totalInstanceCount; }
    public void setTotalInstanceCount(Integer totalInstanceCount) { this.totalInstanceCount = totalInstanceCount; }

    public Integer getMigratedCount() { return migratedCount; }
    public void setMigratedCount(Integer migratedCount) { this.migratedCount = migratedCount; }

    public Integer getFailedCount() { return failedCount; }
    public void setFailedCount(Integer failedCount) { this.failedCount = failedCount; }

    public Long getOperatorId() { return operatorId; }
    public void setOperatorId(Long operatorId) { this.operatorId = operatorId; }

    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }

    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WorkflowMigrationPlan that = (WorkflowMigrationPlan) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }
}
