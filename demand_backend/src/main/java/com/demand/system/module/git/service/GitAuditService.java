package com.demand.system.module.git.service;

import com.demand.system.module.git.dto.GitAuditLogVO;

import java.time.LocalDateTime;
import java.util.List;

public interface GitAuditService {

    /**
     * 记录一条审计日志（operatorIp/userAgent 从当前请求自动提取）
     *
     * @param action     操作类型，如 create/update/delete/archive/approve/merge/toggle
     * @param targetType 目标类型，如 git_platform/git_repository/protection_rule/merge_request
     * @param targetId   目标 ID
     * @param targetName 目标名称
     * @param detail     JSON 格式的操作详情
     */
    void record(String action, String targetType, Long targetId, String targetName, String detail);

    /**
     * 查询审计日志（keyword/operatorId/targetType/action/dateRange 筛选）
     */
    List<GitAuditLogVO> listAuditLogs(String keyword, Long operatorId, String targetType, String action,
                                      LocalDateTime startTime, LocalDateTime endTime);
}
