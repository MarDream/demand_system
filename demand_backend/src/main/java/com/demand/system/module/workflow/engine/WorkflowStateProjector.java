package com.demand.system.module.workflow.engine;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.demand.system.module.workflow.entity.NodeStatus;
import com.demand.system.module.workflow.entity.WorkflowNode;
import com.demand.system.module.workflow.entity.WorkflowState;
import com.demand.system.module.workflow.entity.WorkflowTransition;
import com.demand.system.module.workflow.mapper.NodeStatusMapper;
import com.demand.system.module.workflow.mapper.WorkflowStateMapper;
import com.demand.system.module.workflow.mapper.WorkflowTransitionMapper;
import com.demand.system.module.workflow.support.WorkflowNodeUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class WorkflowStateProjector {

    private final WorkflowStateMapper stateMapper;
    private final WorkflowTransitionMapper transitionMapper;
    private final NodeStatusMapper nodeStatusMapper;

    public WorkflowStateProjector(WorkflowStateMapper stateMapper, WorkflowTransitionMapper transitionMapper,
                                  NodeStatusMapper nodeStatusMapper) {
        this.stateMapper = stateMapper;
        this.transitionMapper = transitionMapper;
        this.nodeStatusMapper = nodeStatusMapper;
    }

    public void project(Long projectId, List<WorkflowNode> nodes, WorkflowGraphCompiler.CompiledWorkflow compiled) {
        transitionMapper.delete(new LambdaQueryWrapper<WorkflowTransition>()
                .eq(WorkflowTransition::getProjectId, projectId));
        stateMapper.delete(new LambdaQueryWrapper<WorkflowState>()
                .eq(WorkflowState::getProjectId, projectId));

        Map<String, WorkflowNode> nodeMap = new HashMap<>();
        if (nodes != null) {
            for (WorkflowNode node : nodes) {
                if (node != null && StringUtils.hasText(node.getNodeId())) {
                    nodeMap.put(node.getNodeId(), node);
                }
            }
        }

        Map<String, Long> stateIdByNodeId = new HashMap<>();
        int sortOrder = 1;
        for (String waitNodeId : compiled.waitNodeIds()) {
            WorkflowNode node = nodeMap.get(waitNodeId);
            if (node == null || "start".equalsIgnoreCase(node.getNodeType())) {
                continue;
            }
            WorkflowState state = new WorkflowState();
            state.setProjectId(projectId);
            state.setName(resolveStateName(node));
            state.setColor("#409EFF");
            state.setIsFinal("end".equalsIgnoreCase(node.getNodeType()) ? 1 : 0);
            state.setSortOrder(sortOrder++);
            stateMapper.insert(state);
            stateIdByNodeId.put(waitNodeId, state.getId());
        }

        for (WorkflowGraphCompiler.FlattenedTransition transition : compiled.transitions()) {
            Long fromStateId = stateIdByNodeId.get(transition.fromNodeId());
            Long toStateId = stateIdByNodeId.get(transition.toNodeId());
            if (fromStateId == null || toStateId == null) {
                continue;
            }
            WorkflowTransition workflowTransition = new WorkflowTransition();
            workflowTransition.setProjectId(projectId);
            workflowTransition.setFromStateId(fromStateId);
            workflowTransition.setToStateId(toStateId);
            transitionMapper.insert(workflowTransition);
        }
    }

    private String resolveStateName(WorkflowNode node) {
        String code = WorkflowNodeUtils.resolveNodeStatusCode(node, false);
        if (StringUtils.hasText(code)) {
            NodeStatus nodeStatus = nodeStatusMapper.selectOne(new LambdaQueryWrapper<NodeStatus>()
                    .eq(NodeStatus::getCode, code)
                    .last("LIMIT 1"));
            if (nodeStatus != null && StringUtils.hasText(nodeStatus.getName())) {
                return nodeStatus.getName();
            }
            return code;
        }
        return node.getNodeName();
    }
}
