package com.demand.system.module.assistant.agent.dto;

import java.util.ArrayList;
import java.util.List;

/** 智能体执行轨迹（assistant_messages.agent_trace 落库，前端回放完整过程） */
public class AgentTrace {

    /** 路由结论：agent_orchestrated（智能体编排）及触发原因 */
    private String routeReason;
    private AgentPlan plan;
    private List<SubAgentRun> subAgents;
    /** 单 Agent 路径的工具调用（子 Agent 路径记录在各自 run 内） */
    private List<ToolCallRecord> toolCalls;
    /** 主循环/各子 Agent 的工具循环轮数 */
    private int rounds;

    public AgentTrace() {
        this.subAgents = new ArrayList<>();
        this.toolCalls = new ArrayList<>();
    }

    public String getRouteReason() { return routeReason; }
    public void setRouteReason(String routeReason) { this.routeReason = routeReason; }

    public AgentPlan getPlan() { return plan; }
    public void setPlan(AgentPlan plan) { this.plan = plan; }

    public List<SubAgentRun> getSubAgents() { return subAgents; }
    public void setSubAgents(List<SubAgentRun> subAgents) { this.subAgents = subAgents; }

    public List<ToolCallRecord> getToolCalls() { return toolCalls; }
    public void setToolCalls(List<ToolCallRecord> toolCalls) { this.toolCalls = toolCalls; }

    public int getRounds() { return rounds; }
    public void setRounds(int rounds) { this.rounds = rounds; }
}
