package com.demand.system.module.statistics.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface StatisticsMapper {

    List<Map<String, Object>> getStatusDistribution(@Param("projectId") Long projectId);

    List<Map<String, Object>> getTypeDistribution(@Param("projectId") Long projectId);

    List<Map<String, Object>> getPriorityDistribution(@Param("projectId") Long projectId);

    List<Map<String, Object>> getStatusDistributionWithOrgFilter(@Param("projectId") Long projectId, @Param("isSuperAdmin") boolean isSuperAdmin, @Param("visibleOrgIds") List<Long> visibleOrgIds);

    List<Map<String, Object>> getTypeDistributionWithOrgFilter(@Param("projectId") Long projectId, @Param("isSuperAdmin") boolean isSuperAdmin, @Param("visibleOrgIds") List<Long> visibleOrgIds);

    List<Map<String, Object>> getPriorityDistributionWithOrgFilter(@Param("projectId") Long projectId, @Param("isSuperAdmin") boolean isSuperAdmin, @Param("visibleOrgIds") List<Long> visibleOrgIds);

    /**
     * 总需求数：登录用户可见的全部需求数（排除草稿）
     */
    int getTotalCount(@Param("userId") Long userId);

    /**
     * 总需求数（带数据权限过滤）：登录用户可见的全部需求数（排除草稿）
     */
    int getTotalCountWithOrgFilter(@Param("userId") Long userId, @Param("visibleOrgIds") List<Long> visibleOrgIds, @Param("isSuperAdmin") boolean isSuperAdmin);

    /**
     * 进行中需求：处于开发中节点的需求
     */
    int getInProgressCount(@Param("userId") Long userId);

    /**
     * 进行中需求（带数据权限过滤）：处于开发中节点的需求
     */
    int getInProgressCountWithOrgFilter(@Param("userId") Long userId, @Param("visibleOrgIds") List<Long> visibleOrgIds, @Param("isSuperAdmin") boolean isSuperAdmin);

    /**
     * 已完成需求：处于结束节点的需求
     */
    int getCompletedCount(@Param("userId") Long userId);

    /**
     * 已完成需求（带数据权限过滤）：处于结束节点的需求
     */
    int getCompletedCountWithOrgFilter(@Param("userId") Long userId, @Param("visibleOrgIds") List<Long> visibleOrgIds, @Param("isSuperAdmin") boolean isSuperAdmin);

    /**
     * 已逾期需求：未结束且超过期望完成日期的需求
     */
    int getOverdueCount(@Param("userId") Long userId);

    /**
     * 已逾期需求（带数据权限过滤）：未结束且超过期望完成日期的需求
     */
    int getOverdueCountWithOrgFilter(@Param("userId") Long userId, @Param("visibleOrgIds") List<Long> visibleOrgIds, @Param("isSuperAdmin") boolean isSuperAdmin);

    int getMyTodoCount(@Param("userId") Long userId);

    int getMyTodoCountWithOrgFilter(@Param("userId") Long userId, @Param("isSuperAdmin") boolean isSuperAdmin, @Param("visibleOrgIds") List<Long> visibleOrgIds);

    List<Map<String, Object>> getDurationData(@Param("projectId") Long projectId);

    List<Map<String, Object>> getTrendData(@Param("projectId") Long projectId);

    List<Map<String, Object>> getDurationDataWithOrgFilter(@Param("projectId") Long projectId, @Param("isSuperAdmin") boolean isSuperAdmin, @Param("visibleOrgIds") List<Long> visibleOrgIds);

    List<Map<String, Object>> getBurndownDataWithOrgFilter(@Param("iterationId") Long iterationId, @Param("isSuperAdmin") boolean isSuperAdmin, @Param("visibleOrgIds") List<Long> visibleOrgIds);

    List<Map<String, Object>> getCfdDataWithOrgFilter(@Param("projectId") Long projectId, @Param("isSuperAdmin") boolean isSuperAdmin, @Param("visibleOrgIds") List<Long> visibleOrgIds);

    /**
     * 已办流程数：用户参与过的流转记录对应的需求数（去重）
     */
    Long countProcessedByUserId(@Param("userId") Long userId);

    Long countProcessedByUserIdWithOrgFilter(@Param("userId") Long userId, @Param("isSuperAdmin") boolean isSuperAdmin, @Param("visibleOrgIds") List<Long> visibleOrgIds);

    /**
     * 我发起的流程数：creator_id = userId 的非草稿需求
     */
    Long countInitiatedByUserId(@Param("userId") Long userId);

    Long countInitiatedByUserIdWithOrgFilter(@Param("userId") Long userId, @Param("isSuperAdmin") boolean isSuperAdmin, @Param("visibleOrgIds") List<Long> visibleOrgIds);

    /**
     * 抄送我的流程数：cc_user_ids 包含当前用户的需求（JSON_CONTAINS）
     */
    Long countCcByUserId(@Param("userId") Long userId);

    Long countCcByUserIdWithOrgFilter(@Param("userId") Long userId, @Param("isSuperAdmin") boolean isSuperAdmin, @Param("visibleOrgIds") List<Long> visibleOrgIds);

    Long countMyFollowsByUserId(@Param("userId") Long userId);

    Long countMyFollowsByUserIdWithOrgFilter(@Param("userId") Long userId, @Param("isSuperAdmin") boolean isSuperAdmin, @Param("visibleOrgIds") List<Long> visibleOrgIds);

    Long countMyDraftsByUserId(@Param("userId") Long userId);

    /**
     * 查询所有标记为结束的节点状态码列表
     * @return 结束状态的 code 列表
     */
    List<String> getEndNodeStatusCodes();
}
