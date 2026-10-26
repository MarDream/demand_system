package com.demand.system.module.assistant.agent.dto;

import java.util.List;

/** 智能体任务拆分计划（规划器产出） */
public class AgentPlan {

    List<AgentSubTask> subtasks;

    public AgentPlan() {}

    public AgentPlan(List<AgentSubTask> subtasks) {
        this.subtasks = subtasks;
    }

    public List<AgentSubTask> getSubtasks() { return subtasks; }
    public void setSubtasks(List<AgentSubTask> subtasks) { this.subtasks = subtasks; }

    public boolean isMultiAgent() {
        return subtasks != null && subtasks.size() > 1;
    }
}
