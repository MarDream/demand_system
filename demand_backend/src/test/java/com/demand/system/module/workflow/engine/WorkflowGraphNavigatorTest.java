package com.demand.system.module.workflow.engine;

import com.demand.system.module.requirement.entity.Requirement;
import com.demand.system.module.workflow.entity.WorkflowEdge;
import com.demand.system.module.workflow.entity.WorkflowNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorkflowGraphNavigatorTest {

    private final WorkflowGraphNavigator navigator = new WorkflowGraphNavigator(new WorkflowConditionEvaluator());

    @Test
    void conditionNodeUsesOnlyExplicitMatchingBranch() {
        WorkflowGraphContext context = contextWithConditionEdges();
        Requirement requirement = new Requirement();
        requirement.setPriority("HIGH");

        List<WorkflowNode> next = navigator.resolveNextWaitNodes(context, "condition", requirement);

        assertEquals(List.of("high"), next.stream().map(WorkflowNode::getNodeId).toList());
    }

    @Test
    void conditionNodeUsesDefaultBranchWhenNoExplicitConditionMatches() {
        WorkflowGraphContext context = contextWithConditionEdges();
        Requirement requirement = new Requirement();
        requirement.setPriority("LOW");

        List<WorkflowNode> next = navigator.resolveNextWaitNodes(context, "condition", requirement);

        assertEquals(List.of("normal"), next.stream().map(WorkflowNode::getNodeId).toList());
    }

    private WorkflowGraphContext contextWithConditionEdges() {
        WorkflowNode condition = node("condition", "condition");
        WorkflowNode high = node("high", "approval");
        WorkflowNode normal = node("normal", "approval");

        WorkflowEdge highEdge = edge("condition", "high");
        highEdge.setCondition(Map.of(
                "logic", "AND",
                "rules", List.of(Map.of("field", "priority", "operator", "eq", "value", "HIGH"))
        ));
        WorkflowEdge defaultEdge = edge("condition", "normal");
        defaultEdge.setCondition(Map.of("defaultFlow", true));

        return WorkflowGraphContext.from(List.of(condition, high, normal), List.of(highEdge, defaultEdge));
    }

    private WorkflowNode node(String id, String type) {
        WorkflowNode node = new WorkflowNode();
        node.setNodeId(id);
        node.setNodeType(type);
        node.setNodeName(id);
        return node;
    }

    private WorkflowEdge edge(String source, String target) {
        WorkflowEdge edge = new WorkflowEdge();
        edge.setSourceNodeId(source);
        edge.setTargetNodeId(target);
        return edge;
    }
}
