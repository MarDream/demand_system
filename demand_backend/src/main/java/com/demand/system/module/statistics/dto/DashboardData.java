package com.demand.system.module.statistics.dto;

public class DashboardData {

    private Integer totalReqs;

    private Integer inProgressReqs;

    private Integer completedReqs;

    private Integer overdueReqs;

    private Integer myTodoCount;

    public Integer getTotalReqs() {
        return totalReqs;
    }

    public void setTotalReqs(Integer totalReqs) {
        this.totalReqs = totalReqs;
    }

    public Integer getInProgressReqs() {
        return inProgressReqs;
    }

    public void setInProgressReqs(Integer inProgressReqs) {
        this.inProgressReqs = inProgressReqs;
    }

    public Integer getCompletedReqs() {
        return completedReqs;
    }

    public void setCompletedReqs(Integer completedReqs) {
        this.completedReqs = completedReqs;
    }

    public Integer getOverdueReqs() {
        return overdueReqs;
    }

    public void setOverdueReqs(Integer overdueReqs) {
        this.overdueReqs = overdueReqs;
    }

    public Integer getMyTodoCount() {
        return myTodoCount;
    }

    public void setMyTodoCount(Integer myTodoCount) {
        this.myTodoCount = myTodoCount;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Integer totalReqs;
        private Integer inProgressReqs;
        private Integer completedReqs;
        private Integer overdueReqs;
        private Integer myTodoCount;

        public Builder totalReqs(Integer totalReqs) {
            this.totalReqs = totalReqs;
            return this;
        }

        public Builder inProgressReqs(Integer inProgressReqs) {
            this.inProgressReqs = inProgressReqs;
            return this;
        }

        public Builder completedReqs(Integer completedReqs) {
            this.completedReqs = completedReqs;
            return this;
        }

        public Builder overdueReqs(Integer overdueReqs) {
            this.overdueReqs = overdueReqs;
            return this;
        }

        public Builder myTodoCount(Integer myTodoCount) {
            this.myTodoCount = myTodoCount;
            return this;
        }

        public DashboardData build() {
            DashboardData data = new DashboardData();
            data.totalReqs = this.totalReqs;
            data.inProgressReqs = this.inProgressReqs;
            data.completedReqs = this.completedReqs;
            data.overdueReqs = this.overdueReqs;
            data.myTodoCount = this.myTodoCount;
            return data;
        }
    }
}
