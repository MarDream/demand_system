package com.demand.system.module.review.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.demand.system.module.review.entity.Review;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

public interface ReviewMapper extends BaseMapper<Review> {

    @Select("SELECT r.*, u.real_name as reviewerName FROM reviews r LEFT JOIN users u ON r.reviewer_id = u.id WHERE r.requirement_id = #{requirementId} ORDER BY r.reviewed_at DESC")
    List<Map<String, Object>> selectByRequirementWithReviewer(@Param("requirementId") Long requirementId);

    @Select({
            "<script>",
            "SELECT r.id, r.requirement_id AS requirementId, req.title AS requirementTitle,",
            "       r.reviewer_id AS reviewerId, u.real_name AS reviewerName, r.result,",
            "       r.comment, r.suggestions, r.reviewed_at AS reviewedAt",
            "FROM reviews r",
            "JOIN requirements req ON req.id = r.requirement_id AND req.deleted_at = 0",
            "LEFT JOIN users u ON r.reviewer_id = u.id",
            "WHERE 1 = 1",
            "  <if test='requirementId != null'> AND r.requirement_id = #{requirementId} </if>",
            "  <if test='result != null and result != \"\"'> AND r.result = #{result} </if>",
            "  <if test='keyword != null and keyword != \"\"'>",
            "    AND (req.title LIKE CONCAT('%', #{keyword}, '%') OR r.comment LIKE CONCAT('%', #{keyword}, '%') OR r.suggestions LIKE CONCAT('%', #{keyword}, '%'))",
            "  </if>",
            "  <if test='manageAll == false'>",
            "    AND (r.reviewer_id = #{currentUserId} OR req.creator_id = #{currentUserId})",
            "  </if>",
            "ORDER BY COALESCE(r.reviewed_at, '9999-12-31') DESC, r.id DESC",
            "</script>"
    })
    IPage<Map<String, Object>> selectReviewPage(IPage<Map<String, Object>> page,
                                                @Param("currentUserId") Long currentUserId,
                                                @Param("manageAll") boolean manageAll,
                                                @Param("requirementId") Long requirementId,
                                                @Param("result") String result,
                                                @Param("keyword") String keyword);
}
