package com.demand.system.module.review.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ReviewUpdateDTO {

    @NotNull(message = "评审ID不能为空")
    private Long id;

    @NotBlank(message = "评审结果不能为空")
    private String result;

    private String comment;

    private String suggestions;
}
