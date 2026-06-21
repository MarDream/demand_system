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

    /**
     * 修复 P0：绕过 @TableLogic 过滤，按 ID 查询需求（包括已删除记录）
     * 用于 restore 等需要访问已删除数据的场景
     */
    @Select("SELECT * FROM requirements WHERE id = #{id} LIMIT 1")
    Requirement selectByIdIncludeDeleted(@Param("id") Long id);

    /**
     * 修复 P0：绕过 @TableLogic 过滤，更新需求（包括已删除记录）
     * 用于 restore 操作
     */
    @org.apache.ibatis.annotations.Update("UPDATE requirements SET deleted_at = 0, updated_at = NOW() WHERE id = #{id}")
    int restoreById(@Param("id") Long id);

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
            "  <if test='type != null and type != \"\"'> AND r.type = #{type} </if>",
            "  <if test='priority != null and priority != \"\"'> AND r.priority = #{priority} </if>",
            "  <if test='status != null and status != \"\"'> AND r.status = #{status} </if>",
            "  <if test='assigneeId != null'> AND r.assignee_id = #{assigneeId} </if>",
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
                                      @Param("type") String type,
                                      @Param("priority") String priority,
                                      @Param("status") String status,
                                      @Param("assigneeId") Long assigneeId,
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
            "  <if test='type != null and type != \"\"'> AND r.type = #{type} </if>",
            "  <if test='priority != null and priority != \"\"'> AND r.priority = #{priority} </if>",
            "  <if test='status != null and status != \"\"'> AND r.status = #{status} </if>",
            "  <if test='assigneeId != null'> AND r.assignee_id = #{assigneeId} </if>",
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
            "          AND (rr.code IN",
            "          <foreach collection='roleCodes' item='rc' open='(' separator=',' close=')'>",
            "            #{rc}",
            "          </foreach>",
            "          OR rr.name IN",
            "          <foreach collection='roleCodes' item='roleName' open='(' separator=',' close=')'>",
            "            #{roleName}",
            "          </foreach>)",
            "      ))",
            "      OR (wn.assignee_type = 'SPECIFIED_ROLE_GROUP' AND EXISTS (",
            "        SELECT 1 FROM roles rg",
            "        WHERE rg.role_group_id = wn.assignee_role_group_id",
            "          AND rg.deleted_at = 0",
            "          AND (rg.code IN",
            "          <foreach collection='roleCodes' item='groupRoleCode' open='(' separator=',' close=')'>",
            "            #{groupRoleCode}",
            "          </foreach>",
            "          OR rg.name IN",
            "          <foreach collection='roleCodes' item='groupRoleName' open='(' separator=',' close=')'>",
            "            #{groupRoleName}",
            "          </foreach>)",
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
                                       @Param("type") String type,
                                       @Param("priority") String priority,
                                       @Param("status") String status,
                                       @Param("assigneeId") Long assigneeId,
                                       @Param("keyword") String keyword);

    @Select({
            "<script>",
            "SELECT r.*",
            "FROM requirement_follows rf",
            "JOIN requirements r ON r.id = rf.requirement_id",
            "WHERE rf.user_id = #{userId}",
            "  AND r.deleted_at = 0",
            "  AND r.is_draft = 0",
            "  <if test='projectId != null'> AND r.project_id = #{projectId} </if>",
            "  <if test='type != null and type != \"\"'> AND r.type = #{type} </if>",
            "  <if test='priority != null and priority != \"\"'> AND r.priority = #{priority} </if>",
            "  <if test='status != null and status != \"\"'> AND r.status = #{status} </if>",
            "  <if test='assigneeId != null'> AND r.assignee_id = #{assigneeId} </if>",
            "  <if test='keyword != null and keyword != \"\"'>",
            "    AND (r.title LIKE CONCAT('%', #{keyword}, '%') OR r.description LIKE CONCAT('%', #{keyword}, '%'))",
            "  </if>",
            "  <if test='isSuperAdmin == false'>",
            "    <choose>",
            "      <when test='visibleOrgIds != null and visibleOrgIds.size() &gt; 0'>",
            "        AND r.org_id IN",
            "        <foreach collection='visibleOrgIds' item='orgId' open='(' separator=',' close=')'>",
            "          #{orgId}",
            "        </foreach>",
            "      </when>",
            "      <otherwise>",
            "        AND r.creator_id = #{userId}",
            "      </otherwise>",
            "    </choose>",
            "  </if>",
            "ORDER BY rf.created_at DESC, rf.id DESC",
            "</script>"
    })
    IPage<Requirement> selectMyFollows(IPage<Requirement> page,
                                       @Param("userId") Long userId,
                                       @Param("projectId") Long projectId,
                                       @Param("type") String type,
                                       @Param("priority") String priority,
                                       @Param("status") String status,
                                       @Param("assigneeId") Long assigneeId,
                                       @Param("keyword") String keyword,
                                       @Param("isSuperAdmin") boolean isSuperAdmin,
                                       @Param("visibleOrgIds") List<Long> visibleOrgIds);

    /**
     * 我的已办 - 查询当前用户创建的已提交需求 或 审批过的需求，排除当前待我审批的需求
     * @param userId 当前用户ID
     * @param roleCodes 当前用户角色编码列表
     * @param directOrgIds 当前用户直接所属组织ID列表
     * @param scopedOrgIds 当前用户含子级的组织ID列表
     * @param keyword 关键词搜索（可选）
     */
    @Select({
            "<script>",
            "SELECT DISTINCT r.*",
            "FROM requirements r",
            "WHERE r.deleted_at = 0",
            "  AND r.is_draft = 0",
            "  <if test='projectId != null'> AND r.project_id = #{projectId} </if>",
            "  <if test='type != null and type != \"\"'> AND r.type = #{type} </if>",
            "  <if test='priority != null and priority != \"\"'> AND r.priority = #{priority} </if>",
            "  <if test='status != null and status != \"\"'> AND r.status = #{status} </if>",
            "  <if test='assigneeId != null'> AND r.assignee_id = #{assigneeId} </if>",
            "  AND (",
            "    r.creator_id = #{userId}",
            "    OR EXISTS (",
            "      SELECT 1 FROM workflow_instance_transitions wit",
            "      WHERE wit.requirement_id = r.id AND wit.operator_id = #{userId}",
            "    )",
            "  )",
            "  AND NOT EXISTS (",
            "    SELECT 1",
            "    FROM workflow_instances wi2",
            "    JOIN workflow_nodes wn2 ON wn2.workflow_version_id = wi2.workflow_version_id AND wn2.node_id = wi2.current_node_id",
            "    WHERE wi2.id = r.workflow_instance_id",
            "      AND wi2.status = 'running'",
            "      AND wn2.assignee_type IS NOT NULL",
            "      AND wn2.assignee_type != ''",
            "      AND (",
            "        (wn2.assignee_type = 'SPECIFIED_USER' AND wn2.assignee_user_ids IS NOT NULL",
            "            AND JSON_CONTAINS(wn2.assignee_user_ids, CAST(#{userId} AS JSON)))",
            "        <if test='roleCodes != null and roleCodes.size() &gt; 0'>",
            "          OR (wn2.assignee_type = 'SPECIFIED_ROLE' AND EXISTS (",
            "            SELECT 1 FROM roles rr2",
            "            WHERE rr2.id = wn2.assignee_role_id",
            "              AND rr2.deleted_at = 0",
            "              AND (rr2.code IN",
            "              <foreach collection='roleCodes' item='rc' open='(' separator=',' close=')'>",
            "                #{rc}",
            "              </foreach>",
            "              OR rr2.name IN",
            "              <foreach collection='roleCodes' item='roleName' open='(' separator=',' close=')'>",
            "                #{roleName}",
            "              </foreach>)",
            "          ))",
            "          OR (wn2.assignee_type = 'SPECIFIED_ROLE_GROUP' AND EXISTS (",
            "            SELECT 1 FROM roles rg2",
            "            WHERE rg2.role_group_id = wn2.assignee_role_group_id",
            "              AND rg2.deleted_at = 0",
            "              AND (rg2.code IN",
            "              <foreach collection='roleCodes' item='groupRoleCode' open='(' separator=',' close=')'>",
            "                #{groupRoleCode}",
            "              </foreach>",
            "              OR rg2.name IN",
            "              <foreach collection='roleCodes' item='groupRoleName' open='(' separator=',' close=')'>",
            "                #{groupRoleName}",
            "              </foreach>)",
            "          ))",
            "        </if>",
            "        <if test='directOrgIds != null and directOrgIds.size() &gt; 0'>",
            "          OR (wn2.assignee_type = 'SPECIFIED_ORG' AND (",
            "            (COALESCE(JSON_UNQUOTE(JSON_EXTRACT(wn2.properties, '$.orgScopeType')), 'include_children') = 'current'",
            "              AND wn2.assignee_org_id IN",
            "              <foreach collection='directOrgIds' item='orgId' open='(' separator=',' close=')'>",
            "                #{orgId}",
            "              </foreach>",
            "            )",
            "            <if test='scopedOrgIds != null and scopedOrgIds.size() &gt; 0'>",
            "              OR (COALESCE(JSON_UNQUOTE(JSON_EXTRACT(wn2.properties, '$.orgScopeType')), 'include_children') != 'current'",
            "                AND wn2.assignee_org_id IN",
            "                <foreach collection='scopedOrgIds' item='scopedOrgId' open='(' separator=',' close=')'>",
            "                  #{scopedOrgId}",
            "                </foreach>",
            "              )",
            "            </if>",
            "          ))",
            "        </if>",
            "        OR (wn2.assignee_type = 'PREV_APPROVER' AND #{userId} = (",
            "          SELECT wit2.operator_id FROM workflow_instance_transitions wit2",
            "          WHERE wit2.instance_id = wi2.id",
            "            AND wit2.to_node_id = wi2.current_node_id",
            "          ORDER BY wit2.id DESC",
            "          LIMIT 1",
            "        ))",
            "        OR (wn2.assignee_type = 'CREATOR' AND r.creator_id = #{userId})",
            "      )",
            "  )",
            "  <if test='keyword != null and keyword != \"\"'>",
            "    AND (r.title LIKE CONCAT('%', #{keyword}, '%') OR r.description LIKE CONCAT('%', #{keyword}, '%'))",
            "  </if>",
            "ORDER BY r.updated_at DESC",
            "</script>"
    })
    IPage<Requirement> selectMyDone(IPage<Requirement> page,
                                    @Param("userId") Long userId,
                                    @Param("roleCodes") List<String> roleCodes,
                                    @Param("directOrgIds") List<Long> directOrgIds,
                                    @Param("scopedOrgIds") List<Long> scopedOrgIds,
                                    @Param("projectId") Long projectId,
                                    @Param("type") String type,
                                    @Param("priority") String priority,
                                    @Param("status") String status,
                                    @Param("assigneeId") Long assigneeId,
                                    @Param("keyword") String keyword);

    /**
     * 我的待办 - 使用物化表优化（新版本）
     * @param userId 当前用户ID
     * @param keyword 关键词搜索（可选）
     */
    @Select({
            "<script>",
            "SELECT DISTINCT r.*",
            "FROM requirements r",
            "JOIN requirement_pending_tasks pt ON pt.requirement_id = r.id",
            "WHERE pt.user_id = #{userId}",
            "  AND r.deleted_at = 0",
            "  AND r.is_draft = 0",
            "  <if test='projectId != null'> AND r.project_id = #{projectId} </if>",
            "  <if test='type != null and type != \"\"'> AND r.type = #{type} </if>",
            "  <if test='priority != null and priority != \"\"'> AND r.priority = #{priority} </if>",
            "  <if test='status != null and status != \"\"'> AND r.status = #{status} </if>",
            "  <if test='assigneeId != null'> AND r.assignee_id = #{assigneeId} </if>",
            "  <if test='keyword != null and keyword != \"\"'>",
            "    AND (r.title LIKE CONCAT('%', #{keyword}, '%') OR r.description LIKE CONCAT('%', #{keyword}, '%'))",
            "  </if>",
            " ORDER BY pt.updated_at DESC, r.id DESC",
            "</script>"
    })
    IPage<Requirement> selectMyPendingOptimized(IPage<Requirement> page,
                                                @Param("userId") Long userId,
                                                @Param("projectId") Long projectId,
                                                @Param("type") String type,
                                                @Param("priority") String priority,
                                                @Param("status") String status,
                                                @Param("assigneeId") Long assigneeId,
                                                @Param("keyword") String keyword);

    /**
     * 我的待办 - V2架构重构版（使用workflow_node_assignees关联表）
     * 性能提升：消除JSON_CONTAINS，使用索引JOIN
     */
    @Select({
            "<script>",
            "SELECT DISTINCT r.*",
            "FROM requirements r",
            "INNER JOIN workflow_instances wi ON r.workflow_instance_id = wi.id",
            "INNER JOIN workflow_node_assignees wna ON",
            "    wna.workflow_version_id = wi.workflow_version_id",
            "    AND wna.node_id = wi.current_node_id",
            "WHERE r.deleted_at = 0",
            "  AND r.is_draft = 0",
            "  AND wi.status = 'running'",
            "  AND (",
            "    (wna.assignee_type = 'USER' AND wna.assignee_id = #{userId})",
            "    <if test='roleIds != null and roleIds.size() &gt; 0'>",
            "      OR (wna.assignee_type = 'ROLE' AND wna.assignee_id IN",
            "      <foreach collection='roleIds' item='roleId' open='(' separator=',' close=')'>",
            "        #{roleId}",
            "      </foreach>",
            "      )",
            "    </if>",
            "    <if test='orgIds != null and orgIds.size() &gt; 0'>",
            "      OR (wna.assignee_type = 'ORG' AND wna.assignee_id IN",
            "      <foreach collection='orgIds' item='orgId' open='(' separator=',' close=')'>",
            "        #{orgId}",
            "      </foreach>",
            "      )",
            "    </if>",
            "  )",
            "  <if test='projectId != null'> AND r.project_id = #{projectId} </if>",
            "  <if test='type != null and type != \"\"'> AND r.type = #{type} </if>",
            "  <if test='priority != null and priority != \"\"'> AND r.priority = #{priority} </if>",
            "  <if test='status != null and status != \"\"'> AND r.status = #{status} </if>",
            "  <if test='assigneeId != null'> AND r.assignee_id = #{assigneeId} </if>",
            "  <if test='keyword != null and keyword != \"\"'>",
            "    AND (r.title LIKE CONCAT('%', #{keyword}, '%') OR r.description LIKE CONCAT('%', #{keyword}, '%'))",
            "  </if>",
            "ORDER BY r.updated_at DESC",
            "</script>"
    })
    IPage<Requirement> selectMyPendingV2(IPage<Requirement> page,
                                         @Param("userId") Long userId,
                                         @Param("roleIds") List<Long> roleIds,
                                         @Param("orgIds") List<Long> orgIds,
                                         @Param("projectId") Long projectId,
                                         @Param("type") String type,
                                         @Param("priority") String priority,
                                         @Param("status") String status,
                                         @Param("assigneeId") Long assigneeId,
                                         @Param("keyword") String keyword);

    /**
     * 我的已办 - V2架构重构版（使用workflow_node_assignees关联表）
     * 性能提升：排除当前待办时使用索引JOIN而非JSON_CONTAINS
     */
    @Select({
            "<script>",
            "SELECT DISTINCT r.*",
            "FROM requirements r",
            "WHERE r.deleted_at = 0",
            "  AND r.is_draft = 0",
            "  AND (",
            "    r.creator_id = #{userId}",
            "    OR EXISTS (",
            "      SELECT 1 FROM workflow_instance_transitions wit",
            "      WHERE wit.requirement_id = r.id",
            "        AND wit.operator_id = #{userId}",
            "    )",
            "  )",
            "  AND NOT EXISTS (",
            "    SELECT 1",
            "    FROM workflow_instances wi2",
            "    INNER JOIN workflow_node_assignees wna2 ON",
            "        wna2.workflow_version_id = wi2.workflow_version_id",
            "        AND wna2.node_id = wi2.current_node_id",
            "    WHERE wi2.id = r.workflow_instance_id",
            "      AND wi2.status = 'running'",
            "      AND (",
            "        (wna2.assignee_type = 'USER' AND wna2.assignee_id = #{userId})",
            "        <if test='roleIds != null and roleIds.size() &gt; 0'>",
            "          OR (wna2.assignee_type = 'ROLE' AND wna2.assignee_id IN",
            "          <foreach collection='roleIds' item='roleId' open='(' separator=',' close=')'>",
            "            #{roleId}",
            "          </foreach>",
            "          )",
            "        </if>",
            "        <if test='orgIds != null and orgIds.size() &gt; 0'>",
            "          OR (wna2.assignee_type = 'ORG' AND wna2.assignee_id IN",
            "          <foreach collection='orgIds' item='orgId' open='(' separator=',' close=')'>",
            "            #{orgId}",
            "          </foreach>",
            "          )",
            "        </if>",
            "      )",
            "  )",
            "  <if test='projectId != null'> AND r.project_id = #{projectId} </if>",
            "  <if test='type != null and type != \"\"'> AND r.type = #{type} </if>",
            "  <if test='priority != null and priority != \"\"'> AND r.priority = #{priority} </if>",
            "  <if test='status != null and status != \"\"'> AND r.status = #{status} </if>",
            "  <if test='assigneeId != null'> AND r.assignee_id = #{assigneeId} </if>",
            "  <if test='keyword != null and keyword != \"\"'>",
            "    AND (r.title LIKE CONCAT('%', #{keyword}, '%') OR r.description LIKE CONCAT('%', #{keyword}, '%'))",
            "  </if>",
            "ORDER BY r.updated_at DESC",
            "</script>"
    })
    IPage<Requirement> selectMyDoneV2(IPage<Requirement> page,
                                      @Param("userId") Long userId,
                                      @Param("roleIds") List<Long> roleIds,
                                      @Param("orgIds") List<Long> orgIds,
                                      @Param("projectId") Long projectId,
                                      @Param("type") String type,
                                      @Param("priority") String priority,
                                      @Param("status") String status,
                                      @Param("assigneeId") Long assigneeId,
                                      @Param("keyword") String keyword);
}
