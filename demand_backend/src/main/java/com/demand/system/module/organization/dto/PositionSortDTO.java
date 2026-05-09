package com.demand.system.module.organization.dto;

import lombok.Data;

import java.util.List;

@Data
public class PositionSortDTO {

    private List<SortItem> items;

    @Data
    public static class SortItem {
        private Long id;
        private Integer sortOrder;
    }
}
