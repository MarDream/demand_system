package com.demand.system.module.statistics.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class DistributionData {
    private Map<String, Integer> statusDist;
    private Map<String, Integer> typeDist;
    private Map<String, Integer> priorityDist;
    private Map<String, Integer> assigneeDist;
}
