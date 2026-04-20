package com.demand.system.module.statistics.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface StatisticsMapper {

    @Select("SELECT status, COUNT(*) as count FROM requirements WHERE project_id = #{projectId} AND deleted_at = 0 GROUP BY status")
    List<Map<String, Object>> getStatusDistribution(@Param("projectId") Long projectId);

    @Select("SELECT type, COUNT(*) as count FROM requirements WHERE project_id = #{projectId} AND deleted_at = 0 GROUP BY type")
    List<Map<String, Object>> getTypeDistribution(@Param("projectId") Long projectId);

    @Select("SELECT priority, COUNT(*) as count FROM requirements WHERE project_id = #{projectId} AND deleted_at = 0 GROUP BY priority")
    List<Map<String, Object>> getPriorityDistribution(@Param("projectId") Long projectId);

    @Select("SELECT COUNT(*) FROM requirements WHERE project_id = #{projectId} AND deleted_at = 0")
    int getTotalCount(@Param("projectId") Long projectId);

    @Select("SELECT COUNT(*) FROM requirements WHERE project_id = #{projectId} AND deleted_at = 0 AND status IN ('开发中','测试中','评审中','待评审')")
    int getInProgressCount(@Param("projectId") Long projectId);

    @Select("SELECT COUNT(*) FROM requirements WHERE project_id = #{projectId} AND deleted_at = 0 AND status IN ('已上线','已验收')")
    int getCompletedCount(@Param("projectId") Long projectId);

    @Select("SELECT COUNT(*) FROM requirements WHERE project_id = #{projectId} AND deleted_at = 0 AND due_date IS NOT NULL AND due_date < CURDATE() AND status NOT IN ('已上线','已验收','已取消')")
    int getOverdueCount(@Param("projectId") Long projectId);

    @Select("SELECT COUNT(*) FROM requirements WHERE assignee_id = #{userId} AND deleted_at = 0 AND status NOT IN ('已上线','已验收','已取消')")
    int getMyTodoCount(@Param("userId") Long userId);

    @Select("SELECT DATE(created_at) as date, COUNT(*) as count FROM requirements WHERE project_id = #{projectId} AND deleted_at = 0 GROUP BY DATE(created_at) ORDER BY date")
    List<Map<String, Object>> getTrendData(@Param("projectId") Long projectId);

    @Select("SELECT status, AVG(DATEDIFF(updated_at, created_at)) as avg_days, MAX(DATEDIFF(updated_at, created_at)) as max_days, MIN(DATEDIFF(updated_at, created_at)) as min_days FROM requirements WHERE project_id = #{projectId} AND deleted_at = 0 GROUP BY status")
    List<Map<String, Object>> getDurationData(@Param("projectId") Long projectId);

    @Select("SELECT DATE(created_at) as date, COUNT(*) as total, SUM(CASE WHEN status IN ('已上线','已验收') THEN 1 ELSE 0 END) as completed FROM requirements WHERE iteration_id = #{iterationId} AND deleted_at = 0 GROUP BY DATE(created_at) ORDER BY date")
    List<Map<String, Object>> getBurndownData(@Param("iterationId") Long iterationId);

    @Select("SELECT DATE(created_at) as date, status, COUNT(*) as count FROM requirements WHERE project_id = #{projectId} AND deleted_at = 0 GROUP BY DATE(created_at), status ORDER BY date")
    List<Map<String, Object>> getCfdData(@Param("projectId") Long projectId);
}
