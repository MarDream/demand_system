package com.demand.system.module.statistics.dto;

import java.util.Map;

public class CfdPoint {

    private String date;

    private Map<String, Integer> newData;

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public Map<String, Integer> getNewData() {
        return newData;
    }

    public void setNewData(Map<String, Integer> newData) {
        this.newData = newData;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String date;
        private Map<String, Integer> newData;

        public Builder date(String date) {
            this.date = date;
            return this;
        }

        public Builder newData(Map<String, Integer> newData) {
            this.newData = newData;
            return this;
        }

        public CfdPoint build() {
            CfdPoint point = new CfdPoint();
            point.date = this.date;
            point.newData = this.newData;
            return point;
        }
    }
}
