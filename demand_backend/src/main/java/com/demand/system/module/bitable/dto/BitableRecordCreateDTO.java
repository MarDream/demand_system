package com.demand.system.module.bitable.dto;

import java.util.Map;

/**
 * 多维表格-创建记录行的DTO
 * cells 的 key 为 fieldId，value 为该字段的单元格值
 */
public class BitableRecordCreateDTO {

    private Map<Long, CellValueDTO> cells;

    public Map<Long, CellValueDTO> getCells() {
        return cells;
    }

    public void setCells(Map<Long, CellValueDTO> cells) {
        this.cells = cells;
    }
}
