package com.demand.system.module.assistant.agent.dto;

import java.util.ArrayList;
import java.util.List;

/** 子 Agent 执行状态（SSE subagent 事件与 agent_trace 落库共用） */
public class SubAgentRun {

    private String id;
    /** research / data_analysis / navigation / general */
    private String type;
    private String title;
    private String instruction;
    /** 实际使用的模型标识（多模型分派可见性） */
    private String modelLabel;
    /** pending / running / completed / failed */
    private String status;
    /** 子 Agent 产出的简报（供综合层引用） */
    private String summary;
    private Long startedAt;
    private Long completedAt;
    private List<ToolCallRecord> toolCalls;

    public SubAgentRun() {}

    public SubAgentRun(String id, String type, String title, String instruction) {
        this.id = id;
        this.type = type;
        this.title = title;
        this.instruction = instruction;
        this.status = "pending";
        this.toolCalls = new ArrayList<>();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getInstruction() { return instruction; }
    public void setInstruction(String instruction) { this.instruction = instruction; }

    public String getModelLabel() { return modelLabel; }
    public void setModelLabel(String modelLabel) { this.modelLabel = modelLabel; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public Long getStartedAt() { return startedAt; }
    public void setStartedAt(Long startedAt) { this.startedAt = startedAt; }

    public Long getCompletedAt() { return completedAt; }
    public void setCompletedAt(Long completedAt) { this.completedAt = completedAt; }

    public List<ToolCallRecord> getToolCalls() { return toolCalls; }
    public void setToolCalls(List<ToolCallRecord> toolCalls) { this.toolCalls = toolCalls; }
}
