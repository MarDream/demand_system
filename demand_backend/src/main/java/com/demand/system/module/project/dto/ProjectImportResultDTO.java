package com.demand.system.module.project.dto;

import java.util.List;

public class ProjectImportResultDTO {

    private Integer successCount;

    private Integer failCount;

    private List<FailureDetail> failures;

    public ProjectImportResultDTO() {
    }

    public ProjectImportResultDTO(Integer successCount, Integer failCount, List<FailureDetail> failures) {
        this.successCount = successCount;
        this.failCount = failCount;
        this.failures = failures;
    }

    public Integer getSuccessCount() {
        return successCount;
    }

    public void setSuccessCount(Integer successCount) {
        this.successCount = successCount;
    }

    public Integer getFailCount() {
        return failCount;
    }

    public void setFailCount(Integer failCount) {
        this.failCount = failCount;
    }

    public List<FailureDetail> getFailures() {
        return failures;
    }

    public void setFailures(List<FailureDetail> failures) {
        this.failures = failures;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Integer successCount;
        private Integer failCount;
        private List<FailureDetail> failures;

        public Builder successCount(Integer successCount) {
            this.successCount = successCount;
            return this;
        }

        public Builder failCount(Integer failCount) {
            this.failCount = failCount;
            return this;
        }

        public Builder failures(List<FailureDetail> failures) {
            this.failures = failures;
            return this;
        }

        public ProjectImportResultDTO build() {
            return new ProjectImportResultDTO(successCount, failCount, failures);
        }
    }

    public static class FailureDetail {
        private Integer rowNum;
        private String projectName;
        private String reason;

        public FailureDetail() {
        }

        public FailureDetail(Integer rowNum, String projectName, String reason) {
            this.rowNum = rowNum;
            this.projectName = projectName;
            this.reason = reason;
        }

        public Integer getRowNum() {
            return rowNum;
        }

        public void setRowNum(Integer rowNum) {
            this.rowNum = rowNum;
        }

        public String getProjectName() {
            return projectName;
        }

        public void setProjectName(String projectName) {
            this.projectName = projectName;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private Integer rowNum;
            private String projectName;
            private String reason;

            public Builder rowNum(Integer rowNum) {
                this.rowNum = rowNum;
                return this;
            }

            public Builder projectName(String projectName) {
                this.projectName = projectName;
                return this;
            }

            public Builder reason(String reason) {
                this.reason = reason;
                return this;
            }

            public FailureDetail build() {
                return new FailureDetail(rowNum, projectName, reason);
            }
        }
    }
}
