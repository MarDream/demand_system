package com.demand.system.module.organization.dto;

import java.util.List;

public class PositionSortDTO {

    private List<SortItem> items;

    public List<SortItem> getItems() {
        return items;
    }

    public void setItems(List<SortItem> items) {
        this.items = items;
    }

    public static class SortItem {
        private Long id;
        private Integer sortOrder;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public Integer getSortOrder() {
            return sortOrder;
        }

        public void setSortOrder(Integer sortOrder) {
            this.sortOrder = sortOrder;
        }
    }
}
