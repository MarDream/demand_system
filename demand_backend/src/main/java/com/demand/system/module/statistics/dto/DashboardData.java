package com.demand.system.module.statistics.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DashboardData {
    private Integer totalReqs;
    private Integer inProgressReqs;
    private Integer completedReqs;
    private Integer overdueReqs;
    private Integer myTodoCount;
}
