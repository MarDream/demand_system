package com.demand.system.module.requirement.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.demand.system.module.requirement.entity.RequirementPendingTask;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 需求待办任务Mapper
 *
 * SQL 定义在 src/main/resources/mapper/RequirementPendingTaskMapper.xml
 *
 * 查询优化说明（性能优先）：
 * - user_id 非空时：直接匹配 user_id
 * - role_id 非空时：JOIN user_roles 判断用户是否拥有该角色
 * - role_group_id 非空时：JOIN roles + user_roles 判断用户是否在该角色组
 * - org_id 非空时：JOIN user_organizations 判断用户是否在该组织
 *
 * 索引依赖：
 * - idx_pending_role_id (role_id)
 * - idx_pending_role_group_id (role_group_id)
 * - idx_pending_org_id (org_id)
 * - idx_pending_user_id (user_id) - 原有
 */
public interface RequirementPendingTaskMapper extends BaseMapper<RequirementPendingTask> {

    /**
     * 删除指定需求的所有待办任务
     */
    int deleteByRequirementId(@Param("requirementId") Long requirementId);

    /**
     * 批量删除需求的待办任务
     */
    int deleteByRequirementIds(@Param("requirementIds") List<Long> requirementIds);

    /**
     * 批量插入待办任务（支持角色/角色组/组织范围）
     */
    int insertBatch(@Param("tasks") List<RequirementPendingTask> tasks);

    /**
     * 查询用户的待办需求ID列表
     *
     * 匹配逻辑（满足任一即可）：
     * 1. 直接匹配：pt.user_id = #{userId}
     * 2. 角色匹配：用户拥有 pt.role_id 对应的角色
     * 3. 角色组匹配：用户的角色属于 pt.role_group_id 对应的角色组
     * 4. 组织匹配：用户属于 pt.org_id 对应的组织
     *
     * 性能优化策略：
     * - 第一步：先从用户关联表获取用户的所有角色ID、角色组ID、组织ID
     * - 第二步：用这些ID集合与 requirement_pending_tasks 做 JOIN
     * - 索引利用：user_roles(role_id)、roles(role_group_id)、user_organizations(org_id) 上都有索引
     * - 这样可以避免子查询走全表扫描，利用已有的索引加速
     */
    List<Long> selectPendingRequirementIds(@Param("userId") Long userId,
                                           @Param("projectId") Long projectId,
                                           @Param("limit") int limit);

    /**
     * 统计用户的待办任务数量
     */
    Long countByUserId(@Param("userId") Long userId);

    /**
     * 统计用户的待办任务数量（带数据权限 org 过滤）
     */
    Long countByUserIdWithOrgFilter(@Param("userId") Long userId,
                                    @Param("isSuperAdmin") boolean isSuperAdmin,
                                    @Param("visibleOrgIds") List<Long> visibleOrgIds);

    /**
     * 统计当前流程位置是否已有运行期待办记录
     */
    Long countByCurrentWorkflowPosition(@Param("requirementId") Long requirementId,
                                        @Param("workflowInstanceId") Long workflowInstanceId,
                                        @Param("currentNodeId") String currentNodeId);

    /**
     * 统计用户对指定需求当前流程位置的运行期待办权限
     */
    Long countAccessibleByCurrentWorkflowPositionAndUser(@Param("requirementId") Long requirementId,
                                                         @Param("workflowInstanceId") Long workflowInstanceId,
                                                         @Param("currentNodeId") String currentNodeId,
                                                         @Param("userId") Long userId);

    /**
     * 查询需求当前流程位置的所有直接待办人ID
     */
    List<Long> selectPendingUserIds(@Param("requirementId") Long requirementId,
                                    @Param("workflowInstanceId") Long workflowInstanceId,
                                    @Param("currentNodeId") String currentNodeId);
}
