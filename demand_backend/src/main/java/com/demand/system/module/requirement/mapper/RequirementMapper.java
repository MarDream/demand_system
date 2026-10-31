package com.demand.system.module.requirement.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.demand.system.module.requirement.dto.RequirementMyListQueryDTO;
import com.demand.system.module.requirement.entity.Requirement;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

public interface RequirementMapper extends BaseMapper<Requirement> {

    Integer selectMaxDailySequence(@Param("datePrefix") String datePrefix);

    /**
     * 绕过 @TableLogic 过滤，按 ID 查询需求（包括已删除记录）
     * 用于 restore 等需要访问已删除数据的场景
     */
    Requirement selectByIdIncludeDeleted(@Param("id") Long id);

    /**
     * 绕过 @TableLogic 过滤，更新需求（包括已删除记录）
     * 用于 restore 操作
     */
    int restoreById(@Param("id") Long id);

    Map<String, Object> selectDetailById(@Param("id") Long id);

    IPage<Requirement> selectMyDrafts(IPage<Requirement> page,
                                      @Param("userId") Long userId,
                                      @Param("userDepartmentId") Long userDepartmentId,
                                      @Param("roleCodes") List<String> roleCodes,
                                      @Param("projectId") Long projectId,
                                      @Param("type") String type,
                                      @Param("priority") String priority,
                                      @Param("status") String status,
                                      @Param("assigneeId") Long assigneeId,
                                      @Param("keyword") String keyword,
                                      @Param("keywordScope") String keywordScope,
                                      @Param("nodeStatus") String nodeStatus,
                                      @Param("isOverdue") Boolean isOverdue,
                                      @Param("timeQuery") RequirementMyListQueryDTO timeQuery);

    IPage<Requirement> selectMyFollows(IPage<Requirement> page,
                                       @Param("userId") Long userId,
                                       @Param("projectId") Long projectId,
                                       @Param("type") String type,
                                       @Param("priority") String priority,
                                       @Param("status") String status,
                                       @Param("assigneeId") Long assigneeId,
                                       @Param("keyword") String keyword,
                                       @Param("keywordScope") String keywordScope,
                                       @Param("isSuperAdmin") boolean isSuperAdmin,
                                       @Param("visibleOrgIds") List<Long> visibleOrgIds,
                                       @Param("nodeStatus") String nodeStatus,
                                       @Param("isOverdue") Boolean isOverdue,
                                       @Param("timeQuery") RequirementMyListQueryDTO timeQuery);

    /**
     * 我的抄送：仅返回只读查阅待办（通过 requirement_pending_tasks 的 CC_READ_ONLY 任务命中）。
     */
    IPage<Requirement> selectMyCc(IPage<Requirement> page,
                                  @Param("userId") Long userId,
                                  @Param("projectId") Long projectId,
                                  @Param("type") String type,
                                  @Param("priority") String priority,
                                  @Param("status") String status,
                                  @Param("assigneeId") Long assigneeId,
                                  @Param("keyword") String keyword,
                                  @Param("keywordScope") String keywordScope,
                                  @Param("nodeStatus") String nodeStatus,
                                  @Param("isOverdue") Boolean isOverdue,
                                  @Param("isSuperAdmin") boolean isSuperAdmin,
                                  @Param("visibleOrgIds") List<Long> visibleOrgIds,
                                  @Param("timeQuery") RequirementMyListQueryDTO timeQuery);

    /**
     * 我的全部：合并草稿/待办/已办/关注/抄送，按更新时间倒序去重。
     * 使用 V2 架构（requirement_pending_tasks / workflow_node_assignees 关联表），
     * 避免旧版 JSON_CONTAINS 无法命中索引的问题。
     */
    IPage<Requirement> selectMyAllV2(IPage<Requirement> page,
                                      @Param("userId") Long userId,
                                      @Param("roleIds") List<Long> roleIds,
                                      @Param("orgIds") List<Long> orgIds,
                                      @Param("projectId") Long projectId,
                                      @Param("type") String type,
                                      @Param("priority") String priority,
                                      @Param("status") String status,
                                      @Param("assigneeId") Long assigneeId,
                                      @Param("keyword") String keyword,
                                      @Param("keywordScope") String keywordScope,
                                      @Param("nodeStatus") String nodeStatus,
                                      @Param("isOverdue") Boolean isOverdue,
                                      @Param("isSuperAdmin") boolean isSuperAdmin,
                                      @Param("visibleOrgIds") List<Long> visibleOrgIds,
                                      @Param("timeQuery") RequirementMyListQueryDTO timeQuery);

    /**
     * 我的待办 - 使用运行期待办物化表判定当前处理权限。
     * 直接指定用户时仅 user_id 命中；未指定具体用户时按角色、角色组或组织范围命中。
     */
    IPage<Requirement> selectMyPendingV2(IPage<Requirement> page,
                                         @Param("userId") Long userId,
                                         @Param("roleIds") List<Long> roleIds,
                                         @Param("orgIds") List<Long> orgIds,
                                         @Param("projectId") Long projectId,
                                         @Param("type") String type,
                                         @Param("priority") String priority,
                                         @Param("status") String status,
                                         @Param("assigneeId") Long assigneeId,
                                         @Param("keyword") String keyword,
                                         @Param("keywordScope") String keywordScope,
                                         @Param("nodeStatus") String nodeStatus,
                                         @Param("isOverdue") Boolean isOverdue,
                                         @Param("isSuperAdmin") boolean isSuperAdmin,
                                         @Param("visibleOrgIds") List<Long> visibleOrgIds,
                                         @Param("timeQuery") RequirementMyListQueryDTO timeQuery);

    /**
     * 我的已办 - 查询当前用户创建或已处理的需求，并使用运行期待办物化表排除当前仍待我处理的需求。
     */
    IPage<Requirement> selectMyDoneV2(IPage<Requirement> page,
                                      @Param("userId") Long userId,
                                      @Param("roleIds") List<Long> roleIds,
                                      @Param("orgIds") List<Long> orgIds,
                                      @Param("projectId") Long projectId,
                                      @Param("type") String type,
                                      @Param("priority") String priority,
                                      @Param("status") String status,
                                      @Param("assigneeId") Long assigneeId,
                                      @Param("keyword") String keyword,
                                      @Param("keywordScope") String keywordScope,
                                      @Param("nodeStatus") String nodeStatus,
                                      @Param("isOverdue") Boolean isOverdue,
                                      @Param("isSuperAdmin") boolean isSuperAdmin,
                                      @Param("visibleOrgIds") List<Long> visibleOrgIds,
                                      @Param("timeQuery") RequirementMyListQueryDTO timeQuery);
}
