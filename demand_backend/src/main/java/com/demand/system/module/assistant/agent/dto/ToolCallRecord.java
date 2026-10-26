package com.demand.system.module.assistant.agent.dto;

/** 一次工具调用的执行记录（SSE toolCall 事件与 agent_trace 落库共用） */
public class ToolCallRecord {

    /** 调用 ID（模型返回的 tool_call id，缺失时由循环生成） */
    private String id;
    /** 归属 Agent 标识（单 Agent 路径为 "main"，子 Agent 路径为子任务 ID） */
    private String agentId;
    private String toolName;
    /** 工具中文名（卡片标题） */
    private String title;
    /** 参数预览（确认卡片与执行记录展示） */
    private String argsPreview;
    /** pending / running / awaiting_confirm / confirmed / denied / completed / failed */
    private String status;
    /** 结果预览（完成/失败后的摘要） */
    private String resultPreview;
    private Long startedAt;
    private Long completedAt;

    public ToolCallRecord() {}

    public ToolCallRecord(String id, String agentId, String toolName, String title, String argsPreview) {
        this.id = id;
        this.agentId = agentId;
        this.toolName = toolName;
        this.title = title;
        this.argsPreview = argsPreview;
        this.status = "pending";
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }

    public String getToolName() { return toolName; }
    public void setToolName(String toolName) { this.toolName = toolName; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getArgsPreview() { return argsPreview; }
    public void setArgsPreview(String argsPreview) { this.argsPreview = argsPreview; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getResultPreview() { return resultPreview; }
    public void setResultPreview(String resultPreview) { this.resultPreview = resultPreview; }

    public Long getStartedAt() { return startedAt; }
    public void setStartedAt(Long startedAt) { this.startedAt = startedAt; }

    public Long getCompletedAt() { return completedAt; }
    public void setCompletedAt(Long completedAt) { this.completedAt = completedAt; }

    public void markStarted() {
        this.startedAt = System.currentTimeMillis();
    }

    public void markFinished() {
        this.completedAt = System.currentTimeMillis();
    }
}
