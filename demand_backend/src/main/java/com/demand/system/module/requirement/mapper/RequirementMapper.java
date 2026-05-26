package com.demand.system.module.requirement.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.demand.system.module.requirement.entity.Requirement;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

public interface RequirementMapper extends BaseMapper<Requirement> {

    @Select("SELECT MAX(CAST(SUBSTRING(requirement_no, 17) AS UNSIGNED)) " +
            "FROM requirements " +
            "WHERE requirement_no LIKE CONCAT(#{datePrefix}, '%')")
    Integer selectMaxDailySequence(@Param("datePrefix") String datePrefix);

    @Select("SELECT r.*, u1.real_name as creator_name, u2.real_name as assignee_name, " +
            "(SELECT COUNT(*) FROM requirements WHERE parent_id = r.id AND deleted_at = 0) as child_count " +
            "FROM requirements r " +
            "LEFT JOIN users u1 ON r.creator_id = u1.id " +
            "LEFT JOIN users u2 ON r.assignee_id = u2.id " +
            "WHERE r.id = #{id} AND r.deleted_at = 0")
    Map<String, Object> selectDetailById(@Param("id") Long id);

    @Select({
            "<script>",
            "SELECT r.*",
            "FROM requirements r",
            "WHERE r.deleted_at = 0",
            "  AND r.is_draft = 1",
            "  AND r.last_saved_at IS NOT NULL",
            "  AND r.creator_id = #{userId}",
            "  <if test='projectId != null'> AND r.project_id = #{projectId} </if>",
            "  <if test='keyword != null and keyword != \"\"'>",
            "    AND (r.title LIKE CONCAT('%', #{keyword}, '%') OR r.description LIKE CONCAT('%', #{keyword}, '%'))",
            "  </if>",
            "ORDER BY r.updated_at DESC, r.id DESC",
            "</script>"
    })
    IPage<Requirement> selectMyDrafts(IPage<Requirement> page,
                                      @Param("userId") Long userId,
                                      @Param("userDepartmentId") Long userDepartmentId,
                                      @Param("roleCodes") List<String> roleCodes,
                                      @Param("projectId") Long projectId,
                                      @Param("keyword") String keyword);

    @Select({
            "<script>",
            "SELECT DISTINCT r.*",
            "FROM requirements r",
            "JOIN workflow_instances wi ON wi.id = r.workflow_instance_id",
            "JOIN workflow_nodes wn ON wn.workflow_version_id = wi.workflow_version_id AND wn.node_id = wi.current_node_id",
            "WHERE r.deleted_at = 0",
            "  AND r.is_draft = 0",
            "  AND wi.status = 'running'",
            "  AND wn.assignee_type IS NOT NULL",
            "  AND wn.assignee_type != ''",
            "  <if test='projectId != null'> AND r.project_id = #{projectId} </if>",
            "  <if test='keyword != null and keyword != \"\"'>",
            "    AND (r.title LIKE CONCAT('%', #{keyword}, '%') OR r.description LIKE CONCAT('%', #{keyword}, '%'))",
            "  </if>",
            "  AND (",
            "    (wn.assignee_type = 'SPECIFIED_USER' AND wn.assignee_user_ids IS NOT NULL",
            "        AND JSON_CONTAINS(wn.assignee_user_ids, CAST(#{userId} AS JSON)))",
            "    <if test='roleCodes != null and roleCodes.size() &gt; 0'>",
            "      OR (wn.assignee_type = 'SPECIFIED_ROLE' AND EXISTS (",
            "        SELECT 1 FROM roles rr",
            "        WHERE rr.id = wn.assignee_role_id",
            "          AND rr.deleted_at = 0",
            "          AND rr.code IN",
            "          <foreach collection='roleCodes' item='rc' open='(' separator=',' close=')'>",
            "            #{rc}",
            "          </foreach>",
            "      ))",
            "      OR (wn.assignee_type = 'SPECIFIED_ROLE_GROUP' AND EXISTS (",
            "        SELECT 1 FROM roles rg",
            "        WHERE rg.role_group_id = wn.assignee_role_group_id",
            "          AND rg.deleted_at = 0",
            "          AND rg.code IN",
            "          <foreach collection='roleCodes' item='groupRoleCode' open='(' separator=',' close=')'>",
            "            #{groupRoleCode}",
            "          </foreach>",
            "      ))",
            "    </if>",
            "    <if test='directOrgIds != null and directOrgIds.size() &gt; 0'>",
            "      OR (wn.assignee_type = 'SPECIFIED_ORG' AND (",
            "        (COALESCE(JSON_UNQUOTE(JSON_EXTRACT(wn.properties, '$.orgScopeType')), 'include_children') = 'current'",
            "          AND wn.assignee_org_id IN",
            "          <foreach collection='directOrgIds' item='orgId' open='(' separator=',' close=')'>",
            "            #{orgId}",
            "          </foreach>",
            "        )",
            "        <if test='scopedOrgIds != null and scopedOrgIds.size() &gt; 0'>",
            "          OR (COALESCE(JSON_UNQUOTE(JSON_EXTRACT(wn.properties, '$.orgScopeType')), 'include_children') != 'current'",
            "            AND wn.assignee_org_id IN",
            "            <foreach collection='scopedOrgIds' item='scopedOrgId' open='(' separator=',' close=')'>",
            "              #{scopedOrgId}",
            "            </foreach>",
            "          )",
            "        </if>",
            "      ))",
            "    </if>",
            "    OR (wn.assignee_type = 'PREV_APPROVER' AND #{userId} = (",
            "      SELECT wit.operator_id FROM workflow_instance_transitions wit",
            "      WHERE wit.instance_id = wi.id",
            "        AND wit.to_node_id = wi.current_node_id",
            "      ORDER BY wit.id DESC",
            "      LIMIT 1",
            "    ))",
            "    OR (wn.assignee_type = 'CREATOR' AND r.creator_id = #{userId})",
            "  )",
            "ORDER BY r.updated_at DESC, r.id DESC",
            "</script>"
    })
    IPage<Requirement> selectMyPending(IPage<Requirement> page,
                                       @Param("userId") Long userId,
                                       @Param("roleCodes") List<String> roleCodes,
                                       @Param("directOrgIds") List<Long> directOrgIds,
                                       @Param("scopedOrgIds") List<Long> scopedOrgIds,
                                       @Param("projectId") Long projectId,
                                       @Param("keyword") String keyword);

    /**
     * 我的已办 - 查询用户参与过审批的需求
     * @param userId 当前用户ID
     * @param isSuperAdmin 是否超级管理员
     * @param keyword 关键词搜索（可选）
     */
    @Select({
            "<script>",
            "SELECT DISTINCT r.*",
            "FROM requirements r",
            "INNER JOIN workflow_instances wi ON wi.id = r.workflow_instance_id",
            "INNER JOIN workflow_instance_transitions wit ON wit.requirement_id = r.id AND wit.operator_id = #{userId}",
            "WHERE r.deleted_at = 0",
            "  AND r.is_draft = 0",
            "  AND wi.status IN ('completed', 'cancelled')",
            "  <if test='keyword != null and keyword != \"\"'>",
            "    AND (r.title LIKE CONCAT('%', #{keyword}, '%') OR r.description LIKE CONCAT('%', #{keyword}, '%'))",
            "  </if>",
            "ORDER BY r.updated_at DESC",
            "</script>"
    })
    List<Requirement> selectMyDone(@Param("userId") Long userId,
                                   @Param("isSuperAdmin") boolean isSuperAdmin,
                                   @Param("keyword") String keyword);
}
