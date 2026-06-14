package com.demand.system.module.review.controller;

import com.demand.system.common.result.PageResult;
import com.demand.system.common.result.Result;
import com.demand.system.module.review.dto.ReviewListQueryDTO;
import com.demand.system.module.review.service.ReviewService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 评审管理顶层接口
 * 对应前端 /api/v1/reviews 调用
 */
@RestController
@RequestMapping("/api/v1/reviews")
public class ReviewsController {

    private final ReviewService reviewService;

    public ReviewsController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public Result<PageResult<Map<String, Object>>> list(ReviewListQueryDTO query) {
        return Result.success(reviewService.list(query));
    }
}
