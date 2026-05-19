package com.demand.system.module.statistics.dto;

public class DurationData {

    private String stateName;

    private Double avgHours;

    private Double maxHours;

    private Double minHours;

    public String getStateName() {
        return stateName;
    }

    public void setStateName(String stateName) {
        this.stateName = stateName;
    }

    public Double getAvgHours() {
        return avgHours;
    }

    public void setAvgHours(Double avgHours) {
        this.avgHours = avgHours;
    }

    public Double getMaxHours() {
        return maxHours;
    }

    public void setMaxHours(Double maxHours) {
        this.maxHours = maxHours;
    }

    public Double getMinHours() {
        return minHours;
    }

    public void setMinHours(Double minHours) {
        this.minHours = minHours;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String stateName;
        private Double avgHours;
        private Double maxHours;
        private Double minHours;

        public Builder stateName(String stateName) {
            this.stateName = stateName;
            return this;
        }

        public Builder avgHours(Double avgHours) {
            this.avgHours = avgHours;
            return this;
        }

        public Builder maxHours(Double maxHours) {
            this.maxHours = maxHours;
            return this;
        }

        public Builder minHours(Double minHours) {
            this.minHours = minHours;
            return this;
        }

        public DurationData build() {
            DurationData data = new DurationData();
            data.stateName = this.stateName;
            data.avgHours = this.avgHours;
            data.maxHours = this.maxHours;
            data.minHours = this.minHours;
            return data;
        }
    }
}
