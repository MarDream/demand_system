package com.demand.system.module.statistics.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BurndownPoint {
    private String date;
    private Integer remaining;
    private Integer completed;
    private Integer total;
}
