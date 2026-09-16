package com.demand.system.module.bitable.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 多维表格-创建数据表分组的DTO
 */
public class BitableTableGroupCreateDTO {

    @NotBlank(message = "分组名称不能为空")
    @Size(max = 100, message = "分组名称不能超过100个字符")
    private String name;

    /**
     * 父分组ID，null 表示创建在根层级
     */
    private Long parentId;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }
}
