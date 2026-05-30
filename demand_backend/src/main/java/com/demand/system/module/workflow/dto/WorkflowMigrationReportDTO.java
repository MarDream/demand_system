package com.demand.system.module.workflow.dto;

import java.util.ArrayList;
import java.util.List;

public class WorkflowMigrationReportDTO {

    private int markedLegacyCount;
    private int backfilledInstanceCount;
    private int migratedRunningInstanceCount;
    private int skippedCount;
    private List<Long> failedRequirementIds = new ArrayList<>();

    public int getMarkedLegacyCount() {
        return markedLegacyCount;
    }

    public void setMarkedLegacyCount(int markedLegacyCount) {
        this.markedLegacyCount = markedLegacyCount;
    }

    public int getBackfilledInstanceCount() {
        return backfilledInstanceCount;
    }

    public void setBackfilledInstanceCount(int backfilledInstanceCount) {
        this.backfilledInstanceCount = backfilledInstanceCount;
    }

    public int getMigratedRunningInstanceCount() {
        return migratedRunningInstanceCount;
    }

    public void setMigratedRunningInstanceCount(int migratedRunningInstanceCount) {
        this.migratedRunningInstanceCount = migratedRunningInstanceCount;
    }

    public int getSkippedCount() {
        return skippedCount;
    }

    public void setSkippedCount(int skippedCount) {
        this.skippedCount = skippedCount;
    }

    public List<Long> getFailedRequirementIds() {
        return failedRequirementIds;
    }

    public void setFailedRequirementIds(List<Long> failedRequirementIds) {
        this.failedRequirementIds = failedRequirementIds;
    }
}
