package com.demand.system.module.review.service;

import com.demand.system.module.review.dto.ReviewConclusionDTO;
import com.demand.system.module.review.dto.ReviewCreateDTO;
import com.demand.system.module.review.dto.ReviewUpdateDTO;

import java.util.List;
import java.util.Map;

public interface ReviewService {

    List<Map<String, Object>> listByRequirement(Long requirementId);

    void create(ReviewCreateDTO dto);

    void update(ReviewUpdateDTO dto);

    ReviewConclusionDTO conclude(Long requirementId);
}
