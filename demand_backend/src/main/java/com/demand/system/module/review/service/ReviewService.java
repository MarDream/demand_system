package com.demand.system.module.review.service;

import com.demand.system.common.result.PageResult;
import com.demand.system.module.review.dto.ReviewConclusionDTO;
import com.demand.system.module.review.dto.ReviewCreateDTO;
import com.demand.system.module.review.dto.ReviewListQueryDTO;
import com.demand.system.module.review.dto.ReviewUpdateDTO;

import java.util.List;
import java.util.Map;

public interface ReviewService {

    PageResult<Map<String, Object>> list(ReviewListQueryDTO query);

    List<Map<String, Object>> listByRequirement(Long requirementId);

    void create(ReviewCreateDTO dto);

    void update(ReviewUpdateDTO dto);

    ReviewConclusionDTO conclude(Long requirementId);
}
