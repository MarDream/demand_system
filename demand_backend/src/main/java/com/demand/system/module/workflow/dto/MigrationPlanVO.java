package com.demand.system.module.workflow.dto;

import java.util.List;

/**
 * 迁移计划 VO
 */
public class MigrationPlanVO {

    private Long id;
    private Long fromVersionId;
    private String fromVersionName;
    private String fromVersion;
    private Long toVersionId;
    private String toVersionName;
    private String toVersion;
    private Long projectId;
    private List<NodeMappingVO> nodeMapping;
    private List<NodeMappingVO> unmappedNodes;
    private String status;
    private Integer totalInstanceCount;
    private Integer migratedCount;
    private Integer failedCount;
    private String operatorName;
    private String remark;
    private String createdAt;
    private List<TargetNodeOption> toVersionNodes; // 目标版本节点列表（供前端下拉选择）

    // ============ 目标节点选项（简化版，供前端下拉选择） ============

    public static class TargetNodeOption {
        private String nodeId;
        private String nodeName;
        private String nodeType;

        public String getNodeId() { return nodeId; }
        public void setNodeId(String nodeId) { this.nodeId = nodeId; }
        public String getNodeName() { return nodeName; }
        public void setNodeName(String nodeName) { this.nodeName = nodeName; }
        public String getNodeType() { return nodeType; }
        public void setNodeType(String nodeType) { this.nodeType = nodeType; }
    }

    // ============ 节点映射 VO ============

    public static class NodeMappingVO {
        private String fromNodeId;
        private String fromNodeName;
        private String fromNodeType;
        private String toNodeId;
        private String toNodeName;
        private String toNodeType;
        private boolean autoMatched;
        private boolean skipped;

        public String getFromNodeId() { return fromNodeId; }
        public void setFromNodeId(String fromNodeId) { this.fromNodeId = fromNodeId; }
        public String getFromNodeName() { return fromNodeName; }
        public void setFromNodeName(String fromNodeName) { this.fromNodeName = fromNodeName; }
        public String getFromNodeType() { return fromNodeType; }
        public void setFromNodeType(String fromNodeType) { this.fromNodeType = fromNodeType; }
        public String getToNodeId() { return toNodeId; }
        public void setToNodeId(String toNodeId) { this.toNodeId = toNodeId; }
        public String getToNodeName() { return toNodeName; }
        public void setToNodeName(String toNodeName) { this.toNodeName = toNodeName; }
        public String getToNodeType() { return toNodeType; }
        public void setToNodeType(String toNodeType) { this.toNodeType = toNodeType; }
        public boolean isAutoMatched() { return autoMatched; }
        public void setAutoMatched(boolean autoMatched) { this.autoMatched = autoMatched; }
        public boolean isSkipped() { return skipped; }
        public void setSkipped(boolean skipped) { this.skipped = skipped; }
    }

    // ============ Getters & Setters ============

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getFromVersionId() { return fromVersionId; }
    public void setFromVersionId(Long fromVersionId) { this.fromVersionId = fromVersionId; }

    public String getFromVersionName() { return fromVersionName; }
    public void setFromVersionName(String fromVersionName) { this.fromVersionName = fromVersionName; }

    public String getFromVersion() { return fromVersion; }
    public void setFromVersion(String fromVersion) { this.fromVersion = fromVersion; }

    public Long getToVersionId() { return toVersionId; }
    public void setToVersionId(Long toVersionId) { this.toVersionId = toVersionId; }

    public String getToVersionName() { return toVersionName; }
    public void setToVersionName(String toVersionName) { this.toVersionName = toVersionName; }

    public String getToVersion() { return toVersion; }
    public void setToVersion(String toVersion) { this.toVersion = toVersion; }

    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }

    public List<NodeMappingVO> getNodeMapping() { return nodeMapping; }
    public void setNodeMapping(List<NodeMappingVO> nodeMapping) { this.nodeMapping = nodeMapping; }

    public List<NodeMappingVO> getUnmappedNodes() { return unmappedNodes; }
    public void setUnmappedNodes(List<NodeMappingVO> unmappedNodes) { this.unmappedNodes = unmappedNodes; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getTotalInstanceCount() { return totalInstanceCount; }
    public void setTotalInstanceCount(Integer totalInstanceCount) { this.totalInstanceCount = totalInstanceCount; }

    public Integer getMigratedCount() { return migratedCount; }
    public void setMigratedCount(Integer migratedCount) { this.migratedCount = migratedCount; }

    public Integer getFailedCount() { return failedCount; }
    public void setFailedCount(Integer failedCount) { this.failedCount = failedCount; }

    public String getOperatorName() { return operatorName; }
    public void setOperatorName(String operatorName) { this.operatorName = operatorName; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public List<TargetNodeOption> getToVersionNodes() { return toVersionNodes; }
    public void setToVersionNodes(List<TargetNodeOption> toVersionNodes) { this.toVersionNodes = toVersionNodes; }
}
