package com.demand.system.module.assistant.agent.tools;

import com.demand.system.module.assistant.agent.core.AgentTool;
import com.demand.system.module.assistant.agent.core.AgentToolContext;
import com.demand.system.module.assistant.agent.core.AgentToolResult;
import com.demand.system.module.assistant.agent.core.ToolParamSchema;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 向用户提问（意图澄清工具，仅主智能体可用）：
 * 当任务目标或关键信息不明确（如报告的口径/范围/格式、缺少归属项目等）时，
 * 模型调用本工具向用户提出澄清问题；循环检测到该调用后把问题作为本轮回答
 * 推送给用户并终止，用户在下一条消息中答复后任务继续。
 */
@Component
public class AskUserTool implements AgentTool {

    public static final String TOOL_NAME = "ask_user";

    @Override
    public String name() {
        return TOOL_NAME;
    }

    @Override
    public String title() {
        return "向用户提问";
    }

    @Override
    public String description() {
        return "当用户任务的关键信息不明确、无法合理假设时，向用户提出澄清问题（例如：报告需要覆盖哪些范围？"
                + "以什么口径统计？输出什么格式？需求要归属到哪个项目？）。"
                + "一次提出所有需要澄清的问题，问题要具体、可一次性回答。"
                + "能从上下文合理推断的信息不要问；用户明确表达过诉求时直接执行，不要问。";
    }

    @Override
    public Map<String, Object> schema() {
        return ToolParamSchema.object()
                .property("question", "string", "要问用户的澄清问题（把所有想问的内容合并成一段完整、友好的话）")
                .required("question")
                .build();
    }

    @Override
    public AgentToolResult execute(AgentToolContext context, JsonNode args) {
        // 实际终止由 AgentLoop 识别工具名处理；此实现仅兜底
        return AgentToolResult.text(args.path("question").asText(""));
    }
}
