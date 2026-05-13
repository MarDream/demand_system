package com.demand.system.module.llm.dto;

import lombok.Data;

@Data
public class SniffedModelVO {
    private String modelId;
    private String ownedBy;
    private boolean alreadyExists;
}
