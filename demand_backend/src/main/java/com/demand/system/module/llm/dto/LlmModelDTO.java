package com.demand.system.module.llm.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class LlmModelDTO {
    @NotBlank(message = "模型名称不能为空")
    private String name;
    @NotBlank(message = "模型标识不能为空")
    private String modelId;
    private String modelType = "general";
    @DecimalMin("0.00") @DecimalMax("1.00")
    private BigDecimal temperature = new BigDecimal("0.30");
    @Min(1) @Max(128000)
    private Integer maxTokens = 2048;
    private Boolean isDefault = false;
    private Boolean enabled = true;
}
