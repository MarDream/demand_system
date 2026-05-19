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
            "  <if test='projectId != null'> AND r.project_id = #{projectId} </if>",
            "  <if test='keyword != null and keyword != \"\"'>",
            "    AND (r.title LIKE CONCAT('%', #{keyword}, '%') OR r.description LIKE CONCAT('%', #{keyword}, '%'))",
            "  </if>",
            "  <if test='orgIds != null and orgIds.size() &gt; 0'>",
            "    AND (r.org_id IN",
            "      <foreach collection='orgIds' item='oid' open='(' separator=',' close=')'>",
            "        #{oid}",
            "      </foreach>",
            "      OR (r.org_id IS NULL AND r.department_id IN",
            "        <foreach collection='orgIds' item='did' open='(' separator=',' close=')'>",
            "          #{did}",
            "        </foreach>",
            "      )",
            "    )",
            "  </if>",
            "  AND (",
            "    r.creator_id = #{userId}",
            "    <if test='departmentId != null and roleCodes != null and roleCodes.size() &gt; 0'>",
            "      OR (r.department_id = #{departmentId} AND (",
            "        <foreach collection='roleCodes' item='rc' separator=' OR '>",
            "          JSON_CONTAINS(r.creator_role_codes, CONCAT('\"', #{rc}, '\"'))",
            "        </foreach>",
            "      ))",
            "    </if>",
            "    <if test='roleCodes != null and roleCodes.size() &gt; 0'>",
            "      OR EXISTS (",
            "        SELECT 1 FROM department_manager_roles dmr",
            "        WHERE dmr.department_id = r.department_id",
            "          AND (",
            "            <foreach collection='roleCodes' item='rc2' separator=' OR '>",
            "              JSON_CONTAINS(dmr.manager_role_codes, CONCAT('\"', #{rc2}, '\"'))",
            "            </foreach>",
            "          )",
            "      )",
            "    </if>",
            "  )",
            "ORDER BY r.updated_at DESC, r.id DESC",
            "</script>"
    })
    IPage<Requirement> selectMyDrafts(IPage<Requirement> page,
                                      @Param("userId") Long userId,
                                      @Param("departmentId") Long departmentId,
                                      @Param("roleCodes") List<String> roleCodes,
                                      @Param("orgIds") List<Long> orgIds,
                                      @Param("projectId") Long projectId,
                                      @Param("keyword") String keyword);

    @Select({
            "<script>",
            "SELECT r.*",
            "FROM requirements r",
            "JOIN workflow_instances wi ON wi.id = r.workflow_instance_id",
            "JOIN workflow_nodes wn ON wn.workflow_version_id = wi.workflow_version_id AND wn.node_id = wi.current_node_id",
            "LEFT JOIN roles rr ON rr.id = wn.assignee_role_id",
            "WHERE r.deleted_at = 0",
            "  AND r.is_draft = 0",
            "  AND wi.status = 'running'",
            "  <if test='projectId != null'> AND r.project_id = #{projectId} </if>",
            "  <if test='keyword != null and keyword != \"\"'>",
            "    AND (r.title LIKE CONCAT('%', #{keyword}, '%') OR r.description LIKE CONCAT('%', #{keyword}, '%'))",
            "  </if>",
            "  <if test='orgIds != null and orgIds.size() &gt; 0'>",
            "    AND (r.org_id IN",
            "      <foreach collection='orgIds' item='oid' open='(' separator=',' close=')'>",
            "        #{oid}",
            "      </foreach>",
            "      OR (r.org_id IS NULL AND r.department_id IN",
            "        <foreach collection='orgIds' item='did' open='(' separator=',' close=')'>",
            "          #{did}",
            "        </foreach>",
            "      )",
            "    )",
            "  </if>",
            "  AND (",
            "    wn.assignee_type IS NULL OR wn.assignee_type = ''",
            "    OR (wn.assignee_type = 'SPECIFIED_USER' AND wn.assignee_user_ids IS NOT NULL",
            "        AND JSON_CONTAINS(wn.assignee_user_ids, CAST(#{userId} AS JSON)))",
            "    <if test='roleCodes != null and roleCodes.size() &gt; 0'>",
            "      OR (wn.assignee_type = 'SPECIFIED_ROLE' AND rr.code IN",
            "        <foreach collection='roleCodes' item='rc' open='(' separator=',' close=')'>",
            "          #{rc}",
            "        </foreach>",
            "      )",
            "    </if>",
            "  )",
            "ORDER BY r.updated_at DESC, r.id DESC",
            "</script>"
    })
    IPage<Requirement> selectMyPending(IPage<Requirement> page,
                                       @Param("userId") Long userId,
                                       @Param("roleCodes") List<String> roleCodes,
                                       @Param("orgIds") List<Long> orgIds,
                                       @Param("projectId") Long projectId,
                                       @Param("keyword") String keyword);
}
