package com.demand.system.module.statistics.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DurationData {
    private String stateName;
    private Double avgHours;
    private Double maxHours;
    private Double minHours;
}
