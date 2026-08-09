package com.demand.system.module.workflow.support;

import com.demand.system.module.workflow.entity.WorkflowNode;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowNodeUtilsTest {

    @Test
    void readsAllowModifyTypeFromDirectNodeProperties() {
        WorkflowNode node = new WorkflowNode();
        node.setProperties(Map.of("allowModifyType", true));

        assertTrue(WorkflowNodeUtils.readBooleanProperty(node, "allowModifyType", false));
    }

    @Test
    void readsAllowModifyTypeFromLegacyNestedProperties() {
        WorkflowNode node = new WorkflowNode();
        node.setProperties(Map.of("properties", Map.of("allowModifyType", "true")));

        assertTrue(WorkflowNodeUtils.readBooleanProperty(node, "allowModifyType", false));
    }

    @Test
    void defaultsAllowModifyTypeToFalseWhenNotConfigured() {
        WorkflowNode node = new WorkflowNode();
        node.setProperties(Map.of());

        assertFalse(WorkflowNodeUtils.readBooleanProperty(node, "allowModifyType", false));
    }
}
