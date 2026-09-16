package com.demand.system.module.bitable.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 多维表格-更新数据表分组的DTO（重命名）
 */
public class BitableTableGroupUpdateDTO {

    @NotBlank(message = "分组名称不能为空")
    @Size(max = 100, message = "分组名称不能超过100个字符")
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
