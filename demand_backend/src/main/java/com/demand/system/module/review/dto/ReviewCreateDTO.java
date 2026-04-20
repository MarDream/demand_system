package com.demand.system.module.review.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ReviewCreateDTO {

    @NotNull(message = "需求ID不能为空")
    private Long requirementId;

    @NotNull(message = "评审人ID不能为空")
    private Long reviewerId;
}
