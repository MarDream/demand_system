package com.demand.system.module.llm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * LLM 功能点模型应用分组-创建DTO
 */
public class LlmApplicationGroupCreateDTO {

    @NotBlank(message = "分组名称不能为空")
    @Size(max = 100, message = "分组名称不能超过100个字符")
    private String name;

    /**
     * 父分组ID，null 表示创建在根层级
     */
    private Long parentId;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
}
