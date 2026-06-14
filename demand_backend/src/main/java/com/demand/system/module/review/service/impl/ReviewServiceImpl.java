package com.demand.system.module.review.service.impl;

import com.demand.system.module.review.dto.ReviewConclusionDTO;
import com.demand.system.module.review.dto.ReviewCreateDTO;
import com.demand.system.module.review.dto.ReviewListQueryDTO;
import com.demand.system.module.review.dto.ReviewUpdateDTO;
import com.demand.system.module.review.entity.Review;
import com.demand.system.module.review.mapper.ReviewMapper;
import com.demand.system.module.review.service.ReviewService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.demand.system.common.exception.BusinessException;
import com.demand.system.common.result.ErrorCode;
import com.demand.system.common.result.PageResult;
import com.demand.system.module.auth.security.SecurityUtils;
import com.demand.system.module.requirement.service.RequirementService;
import com.demand.system.module.user.mapper.UserMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class ReviewServiceImpl implements ReviewService {

    private final ReviewMapper reviewMapper;
    private final RequirementService requirementService;
    private final UserMapper userMapper;

    public ReviewServiceImpl(ReviewMapper reviewMapper,
                             RequirementService requirementService,
                             UserMapper userMapper) {
        this.reviewMapper = reviewMapper;
        this.requirementService = requirementService;
        this.userMapper = userMapper;
    }

    @Override
    public PageResult<Map<String, Object>> list(ReviewListQueryDTO query) {
        Long currentUserId = requireCurrentUserId();
        boolean manageAll = canManageReview();
        Page<Map<String, Object>> page = new Page<>(query.getPageNum(), query.getPageSize());
        var result = reviewMapper.selectReviewPage(page, currentUserId, manageAll, query.getRequirementId(),
                query.getResult(), query.getKeyword());
        return new PageResult<>(result.getRecords(), result.getTotal(), query.getPageNum(), query.getPageSize());
    }

    @Override
    public List<Map<String, Object>> listByRequirement(Long requirementId) {
        ensureRequirementVisible(requirementId);
        return reviewMapper.selectByRequirementWithReviewer(requirementId);
    }

    @Override
    public void create(ReviewCreateDTO dto) {
        ensureRequirementVisible(dto.getRequirementId());
        Long currentUserId = requireCurrentUserId();
        if (dto.getReviewerId() == null || userMapper.selectById(dto.getReviewerId()) == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "评审人不存在");
        }
        if (!Objects.equals(dto.getReviewerId(), currentUserId) && !canManageReview()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权为其他用户创建评审记录");
        }
        Long existingCount = reviewMapper.selectCount(new LambdaQueryWrapper<Review>()
                .eq(Review::getRequirementId, dto.getRequirementId())
                .eq(Review::getReviewerId, dto.getReviewerId()));
        if (existingCount != null && existingCount > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "该评审人已存在评审记录");
        }
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
            throw new BusinessException(ErrorCode.NOT_FOUND, "评审记录不存在");
        }
        ensureRequirementVisible(review.getRequirementId());
        Long currentUserId = requireCurrentUserId();
        if (!Objects.equals(review.getReviewerId(), currentUserId) && !canManageReview()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权修改该评审记录");
        }
        review.setResult(dto.getResult());
        review.setComment(dto.getComment());
        review.setSuggestions(dto.getSuggestions());
        review.setReviewedAt(LocalDateTime.now());
        reviewMapper.updateById(review);
    }

    @Override
    public ReviewConclusionDTO conclude(Long requirementId) {
        ensureRequirementVisible(requirementId);
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

    private void ensureRequirementVisible(Long requirementId) {
        requirementService.getDetail(requirementId);
    }

    private Long requireCurrentUserId() {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "未登录或登录已过期");
        }
        return currentUserId;
    }

    private boolean canManageReview() {
        return SecurityUtils.hasAnyRole("admin", "SUPER_ADMIN", "super_admin")
                || SecurityUtils.hasAnyPermission("button:review:create", "button:review:update");
    }
}
