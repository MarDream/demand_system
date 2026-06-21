package com.demand.system.module.requirement.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.demand.system.module.requirement.entity.RequirementPendingTask;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 需求待办任务Mapper
 */
public interface RequirementPendingTaskMapper extends BaseMapper<RequirementPendingTask> {

    /**
     * 删除指定需求的所有待办任务
     */
    @Delete("DELETE FROM requirement_pending_tasks WHERE requirement_id = #{requirementId}")
    int deleteByRequirementId(@Param("requirementId") Long requirementId);

    /**
     * 批量删除需求的待办任务
     */
    @Delete({
            "<script>",
            "DELETE FROM requirement_pending_tasks",
            "WHERE requirement_id IN",
            "<foreach collection='requirementIds' item='id' open='(' separator=',' close=')'>",
            "  #{id}",
            "</foreach>",
            "</script>"
    })
    int deleteByRequirementIds(@Param("requirementIds") List<Long> requirementIds);

    /**
     * 批量插入待办任务（忽略重复）
     */
    @org.apache.ibatis.annotations.Insert({
            "<script>",
            "INSERT IGNORE INTO requirement_pending_tasks",
            "(requirement_id, user_id, assignee_type, workflow_instance_id, current_node_id, current_node_name)",
            "VALUES",
            "<foreach collection='tasks' item='task' separator=','>",
            "(#{task.requirementId}, #{task.userId}, #{task.assigneeType}, ",
            "#{task.workflowInstanceId}, #{task.currentNodeId}, #{task.currentNodeName})",
            "</foreach>",
            "</script>"
    })
    int insertBatch(@Param("tasks") List<RequirementPendingTask> tasks);

    /**
     * 查询用户的待办需求ID列表（优化后的简单查询）
     */
    @Select({
            "<script>",
            "SELECT DISTINCT pt.requirement_id",
            "FROM requirement_pending_tasks pt",
            "WHERE pt.user_id = #{userId}",
            "<if test='projectId != null'> AND EXISTS (",
            "  SELECT 1 FROM requirements r",
            "  WHERE r.id = pt.requirement_id AND r.project_id = #{projectId}",
            ")</if>",
            "ORDER BY pt.updated_at DESC",
            "LIMIT #{limit}",
            "</script>"
    })
    List<Long> selectPendingRequirementIds(@Param("userId") Long userId,
                                           @Param("projectId") Long projectId,
                                           @Param("limit") int limit);

    /**
     * 统计用户的待办任务数量
     */
    @Select("SELECT COUNT(DISTINCT requirement_id) FROM requirement_pending_tasks WHERE user_id = #{userId}")
    Long countByUserId(@Param("userId") Long userId);

    /**
     * 查询需求的所有待办人ID
     */
    @Select("SELECT DISTINCT user_id FROM requirement_pending_tasks WHERE requirement_id = #{requirementId}")
    List<Long> selectPendingUserIds(@Param("requirementId") Long requirementId);
}
