package com.demand.system.module.assistant.agent.core;

import com.demand.system.module.assistant.agent.dto.AgentPlan;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 规划器 JSON 解析：markdown 包裹、类型归一、非法输出退化单任务 */
class AgentPlannerParseTest {

    private AgentPlanner planner;

    @BeforeEach
    void setUp() {
        planner = new AgentPlanner(null, new ObjectMapper());
    }

    @Test
    void parsesWrappedJsonAndNormalizesType() {
        String content = "```json\n{\"subtasks\":["
                + "{\"type\":\"research\",\"title\":\"查资料\",\"instruction\":\"检索规范\"},"
                + "{\"type\":\"unknown_type\",\"title\":\"查数据\",\"instruction\":\"统计数量\"}"
                + "]}\n```";
        AgentPlan plan = planner.parsePlan(content, "原始问题");
        assertEquals(2, plan.getSubtasks().size());
        assertTrue(plan.isMultiAgent());
        assertEquals("research", plan.getSubtasks().get(0).getType());
        assertEquals("general", plan.getSubtasks().get(1).getType(), "未知类型归一为 general");
    }

    @Test
    void blankInstructionSubtaskSkipped() {
        String content = "{\"subtasks\":["
                + "{\"type\":\"research\",\"title\":\"查资料\",\"instruction\":\"  \"},"
                + "{\"type\":\"data_analysis\",\"title\":\"查数据\",\"instruction\":\"统计\"}"
                + "]}";
        AgentPlan plan = planner.parsePlan(content, "原始问题");
        assertEquals(1, plan.getSubtasks().size());
    }

    @Test
    void invalidJsonFallsBackToSingleTask() {
        AgentPlan plan = planner.parsePlan("抱歉我不会输出 JSON", "原始问题");
        assertEquals(1, plan.getSubtasks().size());
        assertEquals("原始问题", plan.getSubtasks().get(0).getInstruction());
    }

    @Test
    void capsSubtasksToLimit() {
        StringBuilder sb = new StringBuilder("{\"subtasks\":[");
        for (int i = 0; i < 8; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append("{\"type\":\"research\",\"title\":\"t").append(i).append("\",\"instruction\":\"i").append(i).append("\"}");
        }
        sb.append("]}");
        AgentPlan plan = planner.parsePlan(sb.toString(), "原始问题");
        assertEquals(4, plan.getSubtasks().size());
    }
}
