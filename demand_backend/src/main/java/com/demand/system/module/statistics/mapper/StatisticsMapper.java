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

    // 总需求数：登录用户可见的全部需求数（排除草稿）
    @Select("<script>" +
            "SELECT COUNT(DISTINCT r.id) FROM requirements r " +
            "WHERE r.deleted_at = 0 AND (r.is_draft = 0 OR r.is_draft IS NULL) " +
            "AND (" +
            "  EXISTS (SELECT 1 FROM user_roles ur JOIN roles ro ON ur.role_id = ro.id WHERE ur.user_id = #{userId} AND ro.code = 'SUPER_ADMIN') " +
            "  OR r.creator_id = #{userId} " +
            "  OR EXISTS (SELECT 1 FROM workflow_instance_transitions wit WHERE wit.requirement_id = r.id AND wit.operator_id = #{userId}) " +
            "  OR EXISTS (SELECT 1 FROM workflow_instances wi JOIN workflow_nodes wn ON wi.workflow_version_id = wn.workflow_version_id AND wi.current_node_id = wn.node_id WHERE wi.requirement_id = r.id AND JSON_CONTAINS(wn.assignee_user_ids, CAST(#{userId} AS JSON))) " +
            "  OR EXISTS (SELECT 1 FROM user_organizations uo WHERE uo.user_id = #{userId} AND uo.org_id = r.org_id) " +
            ")" +
            "</script>")
    int getTotalCount(@Param("userId") Long userId);

    // 进行中需求：处于开发中节点的需求
    @Select("<script>" +
            "SELECT COUNT(DISTINCT r.id) FROM requirements r " +
            "WHERE r.deleted_at = 0 AND (r.is_draft = 0 OR r.is_draft IS NULL) AND r.node_status = 'IN_DEVELOPMENT' " +
            "AND (" +
            "  EXISTS (SELECT 1 FROM user_roles ur JOIN roles ro ON ur.role_id = ro.id WHERE ur.user_id = #{userId} AND ro.code = 'SUPER_ADMIN') " +
            "  OR r.creator_id = #{userId} " +
            "  OR EXISTS (SELECT 1 FROM workflow_instance_transitions wit WHERE wit.requirement_id = r.id AND wit.operator_id = #{userId}) " +
            "  OR EXISTS (SELECT 1 FROM workflow_instances wi JOIN workflow_nodes wn ON wi.workflow_version_id = wn.workflow_version_id AND wi.current_node_id = wn.node_id WHERE wi.requirement_id = r.id AND JSON_CONTAINS(wn.assignee_user_ids, CAST(#{userId} AS JSON))) " +
            "  OR EXISTS (SELECT 1 FROM user_organizations uo WHERE uo.user_id = #{userId} AND uo.org_id = r.org_id) " +
            ")" +
            "</script>")
    int getInProgressCount(@Param("userId") Long userId);

    // 已完成需求：处于结束节点的需求
    @Select("<script>" +
            "SELECT COUNT(DISTINCT r.id) FROM requirements r " +
            "WHERE r.deleted_at = 0 AND (r.is_draft = 0 OR r.is_draft IS NULL) AND r.node_status = 'ACCEPTED' " +
            "AND (" +
            "  EXISTS (SELECT 1 FROM user_roles ur JOIN roles ro ON ur.role_id = ro.id WHERE ur.user_id = #{userId} AND ro.code = 'SUPER_ADMIN') " +
            "  OR r.creator_id = #{userId} " +
            "  OR EXISTS (SELECT 1 FROM workflow_instance_transitions wit WHERE wit.requirement_id = r.id AND wit.operator_id = #{userId}) " +
            "  OR EXISTS (SELECT 1 FROM workflow_instances wi JOIN workflow_nodes wn ON wi.workflow_version_id = wn.workflow_version_id AND wi.current_node_id = wn.node_id WHERE wi.requirement_id = r.id AND JSON_CONTAINS(wn.assignee_user_ids, CAST(#{userId} AS JSON))) " +
            "  OR EXISTS (SELECT 1 FROM user_organizations uo WHERE uo.user_id = #{userId} AND uo.org_id = r.org_id) " +
            ")" +
            "</script>")
    int getCompletedCount(@Param("userId") Long userId);

    // 已逾期需求：未结束且超过期望完成日期的需求
    @Select("<script>" +
            "SELECT COUNT(DISTINCT r.id) FROM requirements r " +
            "WHERE r.deleted_at = 0 AND (r.is_draft = 0 OR r.is_draft IS NULL) " +
            "AND r.node_status != 'ACCEPTED' AND r.due_date IS NOT NULL AND r.due_date &lt; CURDATE() " +
            "AND (" +
            "  EXISTS (SELECT 1 FROM user_roles ur JOIN roles ro ON ur.role_id = ro.id WHERE ur.user_id = #{userId} AND ro.code = 'SUPER_ADMIN') " +
            "  OR r.creator_id = #{userId} " +
            "  OR EXISTS (SELECT 1 FROM workflow_instance_transitions wit WHERE wit.requirement_id = r.id AND wit.operator_id = #{userId}) " +
            "  OR EXISTS (SELECT 1 FROM workflow_instances wi JOIN workflow_nodes wn ON wi.workflow_version_id = wn.workflow_version_id AND wi.current_node_id = wn.node_id WHERE wi.requirement_id = r.id AND JSON_CONTAINS(wn.assignee_user_ids, CAST(#{userId} AS JSON))) " +
            "  OR EXISTS (SELECT 1 FROM user_organizations uo WHERE uo.user_id = #{userId} AND uo.org_id = r.org_id) " +
            ")" +
            "</script>")
    int getOverdueCount(@Param("userId") Long userId);

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
