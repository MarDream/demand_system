package com.demand.system.module.statistics.dto;

import java.util.Map;

public class DistributionData {

    private Map<String, Integer> statusDist;

    private Map<String, Integer> typeDist;

    private Map<String, Integer> priorityDist;

    private Map<String, Integer> assigneeDist;

    public Map<String, Integer> getStatusDist() {
        return statusDist;
    }

    public void setStatusDist(Map<String, Integer> statusDist) {
        this.statusDist = statusDist;
    }

    public Map<String, Integer> getTypeDist() {
        return typeDist;
    }

    public void setTypeDist(Map<String, Integer> typeDist) {
        this.typeDist = typeDist;
    }

    public Map<String, Integer> getPriorityDist() {
        return priorityDist;
    }

    public void setPriorityDist(Map<String, Integer> priorityDist) {
        this.priorityDist = priorityDist;
    }

    public Map<String, Integer> getAssigneeDist() {
        return assigneeDist;
    }

    public void setAssigneeDist(Map<String, Integer> assigneeDist) {
        this.assigneeDist = assigneeDist;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Map<String, Integer> statusDist;
        private Map<String, Integer> typeDist;
        private Map<String, Integer> priorityDist;
        private Map<String, Integer> assigneeDist;

        public Builder statusDist(Map<String, Integer> statusDist) {
            this.statusDist = statusDist;
            return this;
        }

        public Builder typeDist(Map<String, Integer> typeDist) {
            this.typeDist = typeDist;
            return this;
        }

        public Builder priorityDist(Map<String, Integer> priorityDist) {
            this.priorityDist = priorityDist;
            return this;
        }

        public Builder assigneeDist(Map<String, Integer> assigneeDist) {
            this.assigneeDist = assigneeDist;
            return this;
        }

        public DistributionData build() {
            DistributionData data = new DistributionData();
            data.statusDist = this.statusDist;
            data.typeDist = this.typeDist;
            data.priorityDist = this.priorityDist;
            data.assigneeDist = this.assigneeDist;
            return data;
        }
    }
}
