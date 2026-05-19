package com.demand.system.module.workflow.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class WorkflowVersionMetaUpdateDTO {

    @NotNull(message = "版本号不能为空")
    @Min(value = 1, message = "版本号必须大于0")
    private Integer version;

    @NotBlank(message = "版本名称不能为空")
    @Size(max = 50, message = "版本名称不能超过50个字符")
    private String name;

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
