package com.demand.system.module.review.controller;

import com.demand.system.common.result.Result;
import com.demand.system.module.review.dto.ReviewConclusionDTO;
import com.demand.system.module.review.dto.ReviewCreateDTO;
import com.demand.system.module.review.dto.ReviewUpdateDTO;
import com.demand.system.module.review.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/requirements")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @GetMapping("/{id}/reviews")
    public Result<List<Map<String, Object>>> listByRequirement(@PathVariable Long id) {
        return Result.success(reviewService.listByRequirement(id));
    }

    @PostMapping("/{id}/reviews")
    public Result<Void> create(@PathVariable Long id, @Valid @RequestBody ReviewCreateDTO dto) {
        dto.setRequirementId(id);
        reviewService.create(dto);
        return Result.success();
    }

    @PutMapping("/reviews/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody ReviewUpdateDTO dto) {
        dto.setId(id);
        reviewService.update(dto);
        return Result.success();
    }

    @PostMapping("/{id}/reviews/conclude")
    public Result<ReviewConclusionDTO> conclude(@PathVariable Long id) {
        return Result.success(reviewService.conclude(id));
    }
}
