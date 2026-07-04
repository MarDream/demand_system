package com.demand.system.module.statistics.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface StatisticsMapper {

    /**
     * 获取状态分布统计
     */
    List<Map<String, Object>> getStatusDistribution(@Param("projectId") Long projectId);

    /**
     * 获取需求类型分布统计
     */
    List<Map<String, Object>> getTypeDistribution(@Param("projectId") Long projectId);

    /**
     * 获取优先级分布统计
     */
    List<Map<String, Object>> getPriorityDistribution(@Param("projectId") Long projectId);

    /**
     * 总需求数：登录用户可见的全部需求数（排除草稿）
     */
    int getTotalCount(@Param("userId") Long userId);

    /**
     * 进行中需求：处于开发中节点的需求
     */
    int getInProgressCount(@Param("userId") Long userId);

    /**
     * 已完成需求：处于结束节点的需求
     */
    int getCompletedCount(@Param("userId") Long userId);

    /**
     * 已逾期需求：未结束且超过期望完成日期的需求
     */
    int getOverdueCount(@Param("userId") Long userId);

    /**
     * 我的待办数量
     */
    int getMyTodoCount(@Param("userId") Long userId);

    /**
     * 需求趋势数据
     */
    List<Map<String, Object>> getTrendData(@Param("projectId") Long projectId);

    /**
     * 需求耗时数据
     */
    List<Map<String, Object>> getDurationData(@Param("projectId") Long projectId);

    /**
     * 燃尽图数据
     */
    List<Map<String, Object>> getBurndownData(@Param("iterationId") Long iterationId);

    /**
     * 累积流图数据
     */
    List<Map<String, Object>> getCfdData(@Param("projectId") Long projectId);

    /**
     * 已办流程数：用户参与过的流转记录对应的需求数（去重）
     */
    Long countProcessedByUserId(@Param("userId") Long userId);

    /**
     * 我发起的流程数：creator_id = userId 的非草稿需求
     */
    Long countInitiatedByUserId(@Param("userId") Long userId);

    /**
     * 抄送我的流程数：cc_user_ids 包含当前用户的需求（JSON_CONTAINS）
     */
    Long countCcByUserId(@Param("userId") Long userId);

    /**
     * 我的关注数量
     */
    Long countMyFollowsByUserId(@Param("userId") Long userId);

    /**
     * 我的草稿数量
     */
    Long countMyDraftsByUserId(@Param("userId") Long userId);
}
