package com.demand.system.module.workflow.engine;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.demand.system.module.workflow.entity.WorkflowEdge;
import com.demand.system.module.workflow.entity.WorkflowNode;
import com.demand.system.module.workflow.mapper.WorkflowEdgeMapper;
import com.demand.system.module.workflow.mapper.WorkflowNodeMapper;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class WorkflowRuntimeLoader {

    private final WorkflowNodeMapper nodeMapper;
    private final WorkflowEdgeMapper edgeMapper;

    public WorkflowRuntimeLoader(WorkflowNodeMapper nodeMapper, WorkflowEdgeMapper edgeMapper) {
        this.nodeMapper = nodeMapper;
        this.edgeMapper = edgeMapper;
    }

    public WorkflowGraphContext loadContext(Long workflowVersionId) {
        List<WorkflowNode> nodes = nodeMapper.selectList(new LambdaQueryWrapper<WorkflowNode>()
                .eq(WorkflowNode::getWorkflowVersionId, workflowVersionId));
        List<WorkflowEdge> edges = edgeMapper.selectList(new LambdaQueryWrapper<WorkflowEdge>()
                .eq(WorkflowEdge::getWorkflowVersionId, workflowVersionId));
        return WorkflowGraphContext.from(nodes, edges);
    }

    public List<WorkflowNode> loadNodes(Long workflowVersionId) {
        return nodeMapper.selectList(new LambdaQueryWrapper<WorkflowNode>()
                .eq(WorkflowNode::getWorkflowVersionId, workflowVersionId));
    }

    public List<WorkflowEdge> loadEdges(Long workflowVersionId) {
        return edgeMapper.selectList(new LambdaQueryWrapper<WorkflowEdge>()
                .eq(WorkflowEdge::getWorkflowVersionId, workflowVersionId));
    }
}
