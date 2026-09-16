package com.demand.system.module.llm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * LLM 功能点模型应用分组-重命名DTO
 */
public class LlmApplicationGroupUpdateDTO {

    @NotBlank(message = "分组名称不能为空")
    @Size(max = 100, message = "分组名称不能超过100个字符")
    private String name;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
