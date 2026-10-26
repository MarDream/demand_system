package com.demand.system.module.assistant.agent.tools;

import com.demand.system.module.assistant.agent.core.AgentTool;
import com.demand.system.module.knowledge.llm.ToolDefinition;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 工具目录：汇集全部 AgentTool Bean，按子任务类型裁剪工具集 */
@Component
public class AgentToolCatalog {

    private final Map<String, AgentTool> tools = new LinkedHashMap<>();

    private final AskUserTool askUserTool;

    public AgentToolCatalog(KnowledgeSearchTool knowledgeSearchTool,
                            RequirementSearchTool requirementSearchTool,
                            DataQueryTool dataQueryTool,
                            NavigateTool navigateTool,
                            WebSearchTool webSearchTool,
                            CreateRequirementDraftTool createRequirementDraftTool,
                            AskUserTool askUserTool) {
        register(knowledgeSearchTool);
        register(requirementSearchTool);
        register(dataQueryTool);
        register(navigateTool);
        register(webSearchTool);
        register(createRequirementDraftTool);
        this.askUserTool = askUserTool;
    }

    private void register(AgentTool tool) {
        tools.put(tool.name(), tool);
    }

    public AgentTool get(String name) {
        return name == null ? null : tools.get(name);
    }

    public List<AgentTool> all() {
        return new ArrayList<>(tools.values());
    }

    /** 主智能体可用全部工具 + 向用户提问（子 Agent 后台执行无人应答，不允许 ask_user） */
    public List<AgentTool> mainLoopTools() {
        List<AgentTool> result = all();
        result.add(askUserTool);
        return result;
    }

    /** 主循环 / general 子任务可用全部工具 */
    public List<AgentTool> forSubTaskType(String type) {
        return switch (type == null ? "general" : type) {
            case "research" -> byNames("knowledge_search", "requirement_search", "web_search");
            case "data_analysis" -> byNames("data_query");
            case "navigation" -> byNames("navigate");
            default -> all();
        };
    }

    public List<AgentTool> byNames(String... names) {
        List<AgentTool> result = new ArrayList<>();
        for (String name : names) {
            AgentTool tool = tools.get(name);
            if (tool != null) {
                result.add(tool);
            }
        }
        return result;
    }

    public List<ToolDefinition> toDefinitions(Collection<AgentTool> selected) {
        List<ToolDefinition> definitions = new ArrayList<>();
        for (AgentTool tool : selected) {
            definitions.add(new ToolDefinition(tool.name(), tool.description(), tool.schema()));
        }
        return definitions;
    }
}
