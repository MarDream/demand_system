package com.demand.system.module.review.controller;

import com.demand.system.common.result.PageResult;
import com.demand.system.common.result.Result;
import com.demand.system.module.review.dto.ReviewConclusionDTO;
import com.demand.system.module.review.dto.ReviewCreateDTO;
import com.demand.system.module.review.dto.ReviewListQueryDTO;
import com.demand.system.module.review.dto.ReviewUpdateDTO;
import com.demand.system.module.review.service.ReviewService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/requirements")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping("/reviews")
    @PreAuthorize("isAuthenticated()")
    public Result<PageResult<Map<String, Object>>> list(ReviewListQueryDTO query) {
        return Result.success(reviewService.list(query));
    }

    @GetMapping("/{id}/reviews")
    @PreAuthorize("isAuthenticated()")
    public Result<List<Map<String, Object>>> listByRequirement(@PathVariable Long id) {
        return Result.success(reviewService.listByRequirement(id));
    }

    @PostMapping("/{id}/reviews")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN', 'button:review:create')")
    public Result<Void> create(@PathVariable Long id, @Valid @RequestBody ReviewCreateDTO dto) {
        dto.setRequirementId(id);
        reviewService.create(dto);
        return Result.success();
    }

    @PutMapping("/reviews/{id}")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN', 'button:review:update', 'button:review:submit')")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody ReviewUpdateDTO dto) {
        dto.setId(id);
        reviewService.update(dto);
        return Result.success();
    }

    @PostMapping("/{id}/reviews/conclude")
    @PreAuthorize("isAuthenticated()")
    public Result<ReviewConclusionDTO> conclude(@PathVariable Long id) {
        return Result.success(reviewService.conclude(id));
    }
}
