package com.demand.system.module.workflow.service;

import com.demand.system.module.workflow.dto.*;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.List;

/**
 * 工作流复制服务接口
 */
public interface WorkflowCopyService {

    /**
     * 复制工作流版本
     *
     * @param sourceVersionId 源工作流版本ID
     * @param request 复制请求参数
     * @param operatorId 操作人ID
     * @param operatorName 操作人姓名
     * @param ipAddress IP地址
     * @param userAgent User Agent
     * @return 复制结果
     */
    WorkflowCopyResponse copyWorkflow(Long sourceVersionId, WorkflowCopyRequest request, 
                                     Long operatorId, String operatorName,
                                     String ipAddress, String userAgent);

    /**
     * 获取可复制的工作流模板列表
     *
     * @param page 分页参数
     * @param keyword 搜索关键词
     * @param includeMyWorkflows 是否包含我的工作流
     * @param currentUserId 当前用户ID
     * @return 模板列表
     */
    Page<WorkflowTemplateDTO> getTemplates(Page<WorkflowTemplateDTO> page, String keyword, 
                                          Boolean includeMyWorkflows, Long currentUserId);

    /**
     * 验证工作流名称是否冲突
     *
     * @param name 工作流名称
     * @param projectId 项目ID
     * @param currentUserId 当前用户ID
     * @return 是否存在冲突
     */
    boolean checkNameConflict(String name, Long projectId, Long currentUserId);

    /**
     * 生成唯一的工作流名称
     *
     * @param baseName 基础名称
     * @param projectId 项目ID
     * @param currentUserId 当前用户ID
     * @return 唯一名称
     */
    String generateUniqueName(String baseName, Long projectId, Long currentUserId);

    /**
     * 标记工作流为模板
     *
     * @param versionId 工作流版本ID
     * @param isTemplate 是否为模板
     */
    void markAsTemplate(Long versionId, Boolean isTemplate);

    /**
     * 获取工作流溯源树
     *
     * @param versionId 工作流版本ID
     * @return 溯源树
     */
    WorkflowLineageDTO getLineageTree(Long versionId);
}
