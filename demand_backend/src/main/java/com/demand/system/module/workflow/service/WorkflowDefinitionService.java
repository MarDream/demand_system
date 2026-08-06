package com.demand.system.module.workflow.service;

import com.demand.system.module.workflow.dto.WorkflowDefinitionInfoDTO;

import java.util.List;

/**
 * 工作流定义服务。
 * <p>
 * 管理独立工作流实体（承载工作流名称），并提供历史数据回填能力。
 */
public interface WorkflowDefinitionService {

    /**
     * 列出全部工作流定义（供前端绑定下拉第一级使用）。
     *
     * @return 工作流定义列表
     */
    List<WorkflowDefinitionInfoDTO> listAll();

    /**
     * 历史数据回填：扫描 workflow_versions，按 (projectId, name) 聚合生成 workflow_definitions，
     * 并回填 workflow_versions.workflow_definition_id。
     *
     * @return 回填的版本记录数
     */
    int backfill();
}
