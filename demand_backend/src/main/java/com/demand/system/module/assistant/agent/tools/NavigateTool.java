package com.demand.system.module.assistant.agent.tools;

import com.demand.system.module.assistant.agent.core.AgentTool;
import com.demand.system.module.assistant.agent.core.AgentToolContext;
import com.demand.system.module.assistant.agent.core.AgentToolResult;
import com.demand.system.module.assistant.agent.core.ToolParamSchema;
import com.demand.system.module.assistant.dto.AssistantOperationAdvice;
import com.demand.system.module.assistant.service.AssistantOperationCatalogService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.Map;

/** 操作导航工具：按用户权限给出系统内页面入口与操作路径 */
@Component
public class NavigateTool implements AgentTool {

    private final AssistantOperationCatalogService catalogService;

    public NavigateTool(AssistantOperationCatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @Override
    public String name() {
        return "navigate";
    }

    @Override
    public String title() {
        return "操作导航";
    }

    @Override
    public String description() {
        return "查询系统内的功能入口：返回当前用户有权限访问的页面路径与操作建议。"
                + "适合「在哪里配置/怎么新建/入口在哪」类操作问题。";
    }

    @Override
    public Map<String, Object> schema() {
        return ToolParamSchema.object()
                .property("question", "string", "用户想完成的操作（如：新建需求、配置工作流）")
                .required("question")
                .build();
    }

    @Override
    public AgentToolResult execute(AgentToolContext context, JsonNode args) throws Exception {
        String question = args.path("question").asText("");
        if (question.isBlank()) {
            return AgentToolResult.text("导航查询失败：question 参数不能为空");
        }
        context.log("info", "操作导航查询：" + question);
        AssistantOperationAdvice advice = catalogService.advise(
                question, context.getPageContext(), context.getPermissions(), context.isSuperAdmin());
        if (advice.getActions() == null || advice.getActions().isEmpty()) {
            return AgentToolResult.text("未找到与「" + question + "」匹配的入口。参考建议："
                    + advice.getFallbackAnswer());
        }
        StringBuilder sb = new StringBuilder("系统内相关入口：\n");
        advice.getActions().forEach(action -> sb.append("- ")
                .append(action.getLabel())
                .append("（").append(action.getTargetPath()).append("）：")
                .append(action.getDescription() == null ? "" : action.getDescription())
                .append('\n'));
        return AgentToolResult.builder(sb.toString())
                .uiSummary("找到 " + advice.getActions().size() + " 个相关入口")
                .build();
    }
}
