package com.demand.system.module.project.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectImportResultDTO {

    private Integer successCount;

    private Integer failCount;

    private List<FailureDetail> failures;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FailureDetail {
        private Integer rowNum;
        private String projectName;
        private String reason;
    }
}
