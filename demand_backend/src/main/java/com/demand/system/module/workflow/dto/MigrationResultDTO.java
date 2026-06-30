package com.demand.system.module.workflow.dto;

import java.util.Collections;
import java.util.List;

/**
 * 迁移执行结果 DTO
 */
public class MigrationResultDTO {
    private Integer totalCount;
    private Integer successCount;
    private Integer failedCount;
    private String message;
    private Long planId;
    private List<String> warnings;

    public MigrationResultDTO() {}

    public MigrationResultDTO(Integer totalCount, Integer successCount, Integer failedCount, String message) {
        this(totalCount, successCount, failedCount, message, null, Collections.emptyList());
    }

    public MigrationResultDTO(Integer totalCount, Integer successCount, Integer failedCount,
                               String message, Long planId, List<String> warnings) {
        this.totalCount = totalCount;
        this.successCount = successCount;
        this.failedCount = failedCount;
        this.message = message;
        this.planId = planId;
        this.warnings = warnings != null ? warnings : Collections.emptyList();
    }

    public Integer getTotalCount() { return totalCount; }
    public void setTotalCount(Integer totalCount) { this.totalCount = totalCount; }

    public Integer getSuccessCount() { return successCount; }
    public void setSuccessCount(Integer successCount) { this.successCount = successCount; }

    public Integer getFailedCount() { return failedCount; }
    public void setFailedCount(Integer failedCount) { this.failedCount = failedCount; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public Long getPlanId() { return planId; }
    public void setPlanId(Long planId) { this.planId = planId; }

    public List<String> getWarnings() { return warnings; }
    public void setWarnings(List<String> warnings) { this.warnings = warnings; }
}
