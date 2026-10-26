package com.demand.system.module.assistant.agent.core;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/**
 * 智能体可调用的工具。实现类为 Spring Bean，由 {@code AgentToolCatalog} 汇集。
 * <p>权限口径：userId / superAdmin / permissions 在派发前捕获并随上下文传入
 * （SecurityUtils 基于 ThreadLocal，异步线程不可用），工具自行校验是否放行。</p>
 */
public interface AgentTool {

    /** 工具唯一名（模型 function name） */
    String name();

    /** 工具中文名（前端卡片标题） */
    String title();

    /** 能力描述（下发给模型，决定模型是否会选它） */
    String description();

    /** JSON Schema（object 根） */
    Map<String, Object> schema();

    /** 写操作工具返回 true：执行前需用户在前端确认 */
    default boolean needsConfirmation() {
        return false;
    }

    /**
     * 确认卡片与执行记录展示的参数摘要（默认原样 JSON）。
     * 写工具应重写为可读格式。
     */
    default String previewArgs(JsonNode args) {
        return args == null ? "" : args.toString();
    }

    /** 执行工具；抛出的异常由循环捕获并作为失败结果回传模型 */
    AgentToolResult execute(AgentToolContext context, JsonNode args) throws Exception;
}
