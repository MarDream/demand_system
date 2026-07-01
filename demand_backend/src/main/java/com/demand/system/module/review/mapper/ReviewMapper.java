package com.demand.system.module.review.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.demand.system.module.review.entity.Review;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

public interface ReviewMapper extends BaseMapper<Review> {

    /**
     * 根据需求ID查询评审记录及评审人信息
     *
     * @param requirementId 需求ID
     * @return 评审记录列表
     */
    List<Map<String, Object>> selectByRequirementWithReviewer(@Param("requirementId") Long requirementId);

    /**
     * 分页查询评审记录
     *
     * @param page 分页对象
     * @param currentUserId 当前用户ID
     * @param manageAll 是否查看全部
     * @param requirementId 需求ID（可选）
     * @param result 评审结果（可选）
     * @param keyword 关键字（可选）
     * @return 评审记录分页结果
     */
    IPage<Map<String, Object>> selectReviewPage(IPage<Map<String, Object>> page,
                                                @Param("currentUserId") Long currentUserId,
                                                @Param("manageAll") boolean manageAll,
                                                @Param("requirementId") Long requirementId,
                                                @Param("result") String result,
                                                @Param("keyword") String keyword);
}
