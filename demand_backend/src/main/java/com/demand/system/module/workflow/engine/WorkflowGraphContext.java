package com.demand.system.module.workflow.engine;

import com.demand.system.module.workflow.entity.WorkflowEdge;
import com.demand.system.module.workflow.entity.WorkflowNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class WorkflowGraphContext {

    private final Map<String, WorkflowNode> nodesById;
    private final Map<String, List<WorkflowEdge>> outgoing;

    public WorkflowGraphContext(Map<String, WorkflowNode> nodesById, Map<String, List<WorkflowEdge>> outgoing) {
        this.nodesById = nodesById;
        this.outgoing = outgoing;
    }

    public static WorkflowGraphContext from(List<WorkflowNode> nodes, List<WorkflowEdge> edges) {
        Map<String, WorkflowNode> nodesById = new LinkedHashMap<>();
        if (nodes != null) {
            for (WorkflowNode node : nodes) {
                if (node != null && node.getNodeId() != null) {
                    nodesById.put(node.getNodeId(), node);
                }
            }
        }

        Map<String, List<WorkflowEdge>> outgoing = new LinkedHashMap<>();
        for (String nodeId : nodesById.keySet()) {
            outgoing.put(nodeId, new ArrayList<>());
        }
        if (edges != null) {
            for (WorkflowEdge edge : edges) {
                if (edge == null || edge.getSourceNodeId() == null) {
                    continue;
                }
                outgoing.computeIfAbsent(edge.getSourceNodeId(), key -> new ArrayList<>()).add(edge);
            }
        }
        return new WorkflowGraphContext(nodesById, outgoing);
    }

    public WorkflowNode getNode(String nodeId) {
        return nodesById.get(nodeId);
    }

    public List<WorkflowEdge> outgoing(String nodeId) {
        return outgoing.getOrDefault(nodeId, Collections.emptyList());
    }

    public Map<String, WorkflowNode> nodesById() {
        return nodesById;
    }
}
