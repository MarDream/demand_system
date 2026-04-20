package com.demand.system.module.statistics.dto;

import lombok.Data;

import java.util.Map;

@Data
public class CfdPoint {
    private String date;
    private Map<String, Integer> newData;
}
