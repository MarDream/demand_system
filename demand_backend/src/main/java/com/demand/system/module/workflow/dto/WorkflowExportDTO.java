package com.demand.system.module.workflow.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 工作流导出数据结构
 */
public class WorkflowExportDTO {

    private String exportVersion;
    private LocalDateTime exportedAt;
    private String exportedBy;
    private WorkflowData workflow;

    public static class WorkflowData {
        private String name;
        private String version;
        private Long projectId;
        private WorkflowConfigData config;
        private WorkflowMetadata metadata;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public Long getProjectId() {
            return projectId;
        }

        public void setProjectId(Long projectId) {
            this.projectId = projectId;
        }

        public WorkflowConfigData getConfig() {
            return config;
        }

        public void setConfig(WorkflowConfigData config) {
            this.config = config;
        }

        public WorkflowMetadata getMetadata() {
            return metadata;
        }

        public void setMetadata(WorkflowMetadata metadata) {
            this.metadata = metadata;
        }
    }

    public static class WorkflowConfigData {
        private List<WorkflowNodeDTO> nodes;
        private List<WorkflowEdgeDTO> edges;

        public List<WorkflowNodeDTO> getNodes() {
            return nodes;
        }

        public void setNodes(List<WorkflowNodeDTO> nodes) {
            this.nodes = nodes;
        }

        public List<WorkflowEdgeDTO> getEdges() {
            return edges;
        }

        public void setEdges(List<WorkflowEdgeDTO> edges) {
            this.edges = edges;
        }
    }

    public static class WorkflowMetadata {
        private Long originalVersionId;
        private LocalDateTime originalCreatedAt;
        private LocalDateTime approvedAt;
        private String description;

        public Long getOriginalVersionId() {
            return originalVersionId;
        }

        public void setOriginalVersionId(Long originalVersionId) {
            this.originalVersionId = originalVersionId;
        }

        public LocalDateTime getOriginalCreatedAt() {
            return originalCreatedAt;
        }

        public void setOriginalCreatedAt(LocalDateTime originalCreatedAt) {
            this.originalCreatedAt = originalCreatedAt;
        }

        public LocalDateTime getApprovedAt() {
            return approvedAt;
        }

        public void setApprovedAt(LocalDateTime approvedAt) {
            this.approvedAt = approvedAt;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }

    public String getExportVersion() {
        return exportVersion;
    }

    public void setExportVersion(String exportVersion) {
        this.exportVersion = exportVersion;
    }

    public LocalDateTime getExportedAt() {
        return exportedAt;
    }

    public void setExportedAt(LocalDateTime exportedAt) {
        this.exportedAt = exportedAt;
    }

    public String getExportedBy() {
        return exportedBy;
    }

    public void setExportedBy(String exportedBy) {
        this.exportedBy = exportedBy;
    }

    public WorkflowData getWorkflow() {
        return workflow;
    }

    public void setWorkflow(WorkflowData workflow) {
        this.workflow = workflow;
    }
}
