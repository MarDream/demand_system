package com.demand.system.module.requirement.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.demand.system.module.requirement.entity.Requirement;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

public interface RequirementMapper extends BaseMapper<Requirement> {

    Integer selectMaxDailySequence(@Param("datePrefix") String datePrefix);

    /**
     * 修复 P0：绕过 @TableLogic 过滤，按 ID 查询需求（包括已删除记录）
     * 用于 restore 等需要访问已删除数据的场景
     */
    Requirement selectByIdIncludeDeleted(@Param("id") Long id);

    /**
     * 修复 P0：绕过 @TableLogic 过滤，更新需求（包括已删除记录）
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
                                      @Param("keyword") String keyword);

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
    IPage<Requirement> selectMyPendingOptimized(IPage<Requirement> page,
                                                @Param("userId") Long userId,
                                                @Param("projectId") Long projectId,
                                                @Param("type") String type,
                                                @Param("priority") String priority,
                                                @Param("status") String status,
                                                @Param("assigneeId") Long assigneeId,
                                                @Param("keyword") String keyword);

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
                                         @Param("keyword") String keyword);

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
                                      @Param("keyword") String keyword);
}
