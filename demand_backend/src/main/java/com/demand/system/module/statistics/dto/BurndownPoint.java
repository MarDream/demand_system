package com.demand.system.module.statistics.dto;

public class BurndownPoint {

    private String date;

    private Integer remaining;

    private Integer completed;

    private Integer total;

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public Integer getRemaining() {
        return remaining;
    }

    public void setRemaining(Integer remaining) {
        this.remaining = remaining;
    }

    public Integer getCompleted() {
        return completed;
    }

    public void setCompleted(Integer completed) {
        this.completed = completed;
    }

    public Integer getTotal() {
        return total;
    }

    public void setTotal(Integer total) {
        this.total = total;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String date;
        private Integer remaining;
        private Integer completed;
        private Integer total;

        public Builder date(String date) {
            this.date = date;
            return this;
        }

        public Builder remaining(Integer remaining) {
            this.remaining = remaining;
            return this;
        }

        public Builder completed(Integer completed) {
            this.completed = completed;
            return this;
        }

        public Builder total(Integer total) {
            this.total = total;
            return this;
        }

        public BurndownPoint build() {
            BurndownPoint point = new BurndownPoint();
            point.date = this.date;
            point.remaining = this.remaining;
            point.completed = this.completed;
            point.total = this.total;
            return point;
        }
    }
}
