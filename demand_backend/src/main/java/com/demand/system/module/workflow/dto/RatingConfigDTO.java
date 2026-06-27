package com.demand.system.module.workflow.dto;

import java.io.Serializable;
import java.util.List;

/**
 * 节点评分配置（存储于 workflow_nodes.properties.ratingConfig）
 * 对应 ADR-002 工作流节点评分功能设计
 */
public class RatingConfigDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Boolean enabled;
    private Boolean required;
    private String evaluator;
    private Boolean showInStatistics;
    private List<RatingDimension> dimensions;

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public Boolean getRequired() { return required; }
    public void setRequired(Boolean required) { this.required = required; }
    public String getEvaluator() { return evaluator; }
    public void setEvaluator(String evaluator) { this.evaluator = evaluator; }
    public Boolean getShowInStatistics() { return showInStatistics; }
    public void setShowInStatistics(Boolean showInStatistics) { this.showInStatistics = showInStatistics; }
    public List<RatingDimension> getDimensions() { return dimensions; }
    public void setDimensions(List<RatingDimension> dimensions) { this.dimensions = dimensions; }

    public static class RatingDimension implements Serializable {
        private static final long serialVersionUID = 1L;
        private String key;
        private String name;
        private String description;
        private String minLabel;
        private String maxLabel;

        public String getKey() { return key; }
        public void setKey(String key) { this.key = key; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getMinLabel() { return minLabel; }
        public void setMinLabel(String minLabel) { this.minLabel = minLabel; }
        public String getMaxLabel() { return maxLabel; }
        public void setMaxLabel(String maxLabel) { this.maxLabel = maxLabel; }
    }
}
