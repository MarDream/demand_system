package com.demand.system.module.review.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.demand.system.module.review.entity.Review;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

public interface ReviewMapper extends BaseMapper<Review> {

    @Select("SELECT r.*, u.real_name as reviewer_name FROM reviews r LEFT JOIN users u ON r.reviewer_id = u.id WHERE r.requirement_id = #{requirementId} ORDER BY r.reviewed_at DESC")
    List<Map<String, Object>> selectByRequirementWithReviewer(@Param("requirementId") Long requirementId);
}
