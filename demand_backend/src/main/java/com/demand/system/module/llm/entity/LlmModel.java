package com.demand.system.module.llm.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("llm_models")
public class LlmModel {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long providerId;
    private String name;
    private String modelId;
    private String modelType;
    private BigDecimal temperature;
    private Integer maxTokens;
    private Boolean isDefault;
    private Boolean enabled;
    private Boolean testSuccess;
    private Integer testDuration;
    private String testError;
    private LocalDateTime testAt;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
