package com.demand.system.module.review.service.impl;

import com.demand.system.module.review.dto.ReviewConclusionDTO;
import com.demand.system.module.review.dto.ReviewCreateDTO;
import com.demand.system.module.review.dto.ReviewUpdateDTO;
import com.demand.system.module.review.entity.Review;
import com.demand.system.module.review.mapper.ReviewMapper;
import com.demand.system.module.review.service.ReviewService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private final ReviewMapper reviewMapper;

    @Override
    public List<Map<String, Object>> listByRequirement(Long requirementId) {
        return reviewMapper.selectByRequirementWithReviewer(requirementId);
    }

    @Override
    public void create(ReviewCreateDTO dto) {
        Review review = new Review();
        review.setRequirementId(dto.getRequirementId());
        review.setReviewerId(dto.getReviewerId());
        review.setReviewedAt(null);
        reviewMapper.insert(review);
    }

    @Override
    public void update(ReviewUpdateDTO dto) {
        Review review = reviewMapper.selectById(dto.getId());
        if (review == null) {
            throw new RuntimeException("评审记录不存在");
        }
        review.setResult(dto.getResult());
        review.setComment(dto.getComment());
        review.setSuggestions(dto.getSuggestions());
        review.setReviewedAt(LocalDateTime.now());
        reviewMapper.updateById(review);
    }

    @Override
    public ReviewConclusionDTO conclude(Long requirementId) {
        LambdaQueryWrapper<Review> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Review::getRequirementId, requirementId);
        List<Review> reviews = reviewMapper.selectList(wrapper);

        ReviewConclusionDTO conclusion = new ReviewConclusionDTO();
        conclusion.setTotalReviews(reviews.size());

        long passedCount = reviews.stream().filter(r -> "通过".equals(r.getResult())).count();
        long failedCount = reviews.stream().filter(r -> "不通过".equals(r.getResult())).count();
        long needModificationCount = reviews.stream().filter(r -> "需修改".equals(r.getResult())).count();

        conclusion.setPassedCount((int) passedCount);
        conclusion.setFailedCount((int) failedCount);
        conclusion.setNeedModificationCount((int) needModificationCount);

        if (reviews.isEmpty()) {
            conclusion.setConclusion("无评审记录");
            conclusion.setConclusionDetail("暂无评审记录，无法得出结论");
        } else if (failedCount > 0) {
            conclusion.setConclusion("不通过");
            conclusion.setConclusionDetail("存在不通过的评审记录");
        } else if (needModificationCount > 0) {
            conclusion.setConclusion("需修改");
            conclusion.setConclusionDetail("存在需修改的评审记录");
        } else {
            conclusion.setConclusion("通过");
            conclusion.setConclusionDetail("所有评审记录均为通过");
        }

        return conclusion;
    }
}
