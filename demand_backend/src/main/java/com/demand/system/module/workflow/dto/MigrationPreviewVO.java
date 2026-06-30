package com.demand.system.module.workflow.dto;

import java.util.List;

/**
 * 迁移预检结果 VO
 */
public class MigrationPreviewVO {

    private Integer totalInstances;
    private Integer canMigrateCount;
    private Integer needManualCount;
    private List<InstancePreviewItem> items;

    public static class InstancePreviewItem {
        private Long instanceId;
        private Long requirementId;
        private String currentNodeId;
        private String currentNodeName;
        private boolean mapped;
        private String mappedToNodeId;
        private String mappedToNodeName;

        public Long getInstanceId() { return instanceId; }
        public void setInstanceId(Long instanceId) { this.instanceId = instanceId; }
        public Long getRequirementId() { return requirementId; }
        public void setRequirementId(Long requirementId) { this.requirementId = requirementId; }
        public String getCurrentNodeId() { return currentNodeId; }
        public void setCurrentNodeId(String currentNodeId) { this.currentNodeId = currentNodeId; }
        public String getCurrentNodeName() { return currentNodeName; }
        public void setCurrentNodeName(String currentNodeName) { this.currentNodeName = currentNodeName; }
        public boolean isMapped() { return mapped; }
        public void setMapped(boolean mapped) { this.mapped = mapped; }
        public String getMappedToNodeId() { return mappedToNodeId; }
        public void setMappedToNodeId(String mappedToNodeId) { this.mappedToNodeId = mappedToNodeId; }
        public String getMappedToNodeName() { return mappedToNodeName; }
        public void setMappedToNodeName(String mappedToNodeName) { this.mappedToNodeName = mappedToNodeName; }
    }

    public Integer getTotalInstances() { return totalInstances; }
    public void setTotalInstances(Integer totalInstances) { this.totalInstances = totalInstances; }

    public Integer getCanMigrateCount() { return canMigrateCount; }
    public void setCanMigrateCount(Integer canMigrateCount) { this.canMigrateCount = canMigrateCount; }

    public Integer getNeedManualCount() { return needManualCount; }
    public void setNeedManualCount(Integer needManualCount) { this.needManualCount = needManualCount; }

    public List<InstancePreviewItem> getItems() { return items; }
    public void setItems(List<InstancePreviewItem> items) { this.items = items; }
}
