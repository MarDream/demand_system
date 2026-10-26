package com.demand.system.module.assistant.agent.core;

import com.demand.system.module.assistant.agent.dto.SubAgentRun;
import com.demand.system.module.assistant.agent.dto.ToolCallRecord;
import com.demand.system.module.assistant.dto.AssistantTask;

/** 智能体编排过程中的 SSE 事件出口（由编排器实现并转发到 SseEmitter） */
public interface AgentEventSink {

    /** 子 Agent 状态变更（subagent 事件） */
    void onSubAgent(SubAgentRun run);

    /** 工具调用状态变更（toolCall 事件） */
    void onToolCall(ToolCallRecord record);

    /** 写操作待确认（permissionRequest 事件） */
    void onPermissionRequest(String requestId, ToolCallRecord record);

    /** 任务面板更新（taskUpdate 事件） */
    void onTask(AssistantTask task);
}
