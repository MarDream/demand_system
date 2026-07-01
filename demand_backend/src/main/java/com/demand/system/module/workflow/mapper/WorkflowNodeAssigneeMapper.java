package com.demand.system.module.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.demand.system.module.workflow.entity.WorkflowNodeAssignee;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 工作流节点指派关联表Mapper
 */
@Mapper
public interface WorkflowNodeAssigneeMapper extends BaseMapper<WorkflowNodeAssignee> {

    /**
     * 批量插入指派关系
     * @param assignees 指派关系列表
     * @return 插入成功的记录数
     */
    int batchInsert(@Param("assignees") List<WorkflowNodeAssignee> assignees);

    /**
     * 删除指定版本+节点的所有指派关系
     * @param workflowVersionId 工作流版本ID
     * @param nodeId 节点ID
     * @return 删除的记录数
     */
    int deleteByVersionAndNode(@Param("workflowVersionId") Long workflowVersionId,
                                @Param("nodeId") String nodeId);

    /**
     * 查询用户可见的工作流版本+节点组合（用于权限判断）
     * @param userId 用户ID
     * @return 版本+节点列表
     */
    List<WorkflowNodeAssignee> findByUserId(@Param("userId") Long userId);

    /**
     * 查询指定版本+节点的所有指派关系
     * @param workflowVersionId 工作流版本ID
     * @param nodeId 节点ID
     * @return 指派关系列表
     */
    List<WorkflowNodeAssignee> findByVersionAndNode(@Param("workflowVersionId") Long workflowVersionId,
                                                      @Param("nodeId") String nodeId);

    /**
     * 删除指定工作流版本的所有指派关系
     * @param workflowVersionId 工作流版本ID
     * @return 删除的记录数
     */
    int deleteByVersion(@Param("workflowVersionId") Long workflowVersionId);
}
