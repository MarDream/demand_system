package com.demand.system.module.assistant.agent.tools;

import com.demand.system.module.assistant.agent.core.AgentTool;
import com.demand.system.module.assistant.agent.core.AgentToolContext;
import com.demand.system.module.assistant.agent.core.AgentToolResult;
import com.demand.system.module.assistant.agent.core.ToolParamSchema;
import com.demand.system.module.knowledge.llm.LlmGateway;
import com.demand.system.module.knowledge.llm.LlmGatewayConfig;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.Map;

/** 联网搜索工具：借助当前执行 Agent 所用模型的厂商联网搜索能力 */
@Component
public class WebSearchTool implements AgentTool {

    private final LlmGateway llmGateway;

    public WebSearchTool(LlmGateway llmGateway) {
        this.llmGateway = llmGateway;
    }

    @Override
    public String name() {
        return "web_search";
    }

    @Override
    public String title() {
        return "联网搜索";
    }

    @Override
    public String description() {
        return "搜索互联网获取实时或外部信息（行业资讯、技术资料、通用知识）。"
                + "仅当知识库与数据库无法回答时使用。";
    }

    @Override
    public Map<String, Object> schema() {
        return ToolParamSchema.object()
                .property("query", "string", "搜索关键词或问句")
                .required("query")
                .build();
    }

    @Override
    public AgentToolResult execute(AgentToolContext context, JsonNode args) throws Exception {
        String query = args.path("query").asText("");
        if (query.isBlank()) {
            return AgentToolResult.text("搜索失败：query 参数不能为空");
        }
        LlmGatewayConfig.Provider provider = context.getChatProvider();
        if (provider == null) {
            return AgentToolResult.text("联网搜索不可用：未配置可用模型。");
        }
        context.log("info", "联网搜索：" + query);
        String systemPrompt = "你是联网搜索助手。针对用户问题搜索互联网并直接给出搜索到的结果要点，"
                + "包含关键事实与来源网站名。不要输出 JSON。";
        Map<String, Object> webSearchParams = llmGateway.buildWebSearchParams(provider);
        LlmGateway.ChatResult result = llmGateway.chatWithProviderWithThinking(
                provider, systemPrompt, query, null, null, webSearchParams);
        String content = result.getContent() == null ? "" : result.getContent();
        if (content.isBlank()) {
            return AgentToolResult.text("联网搜索无结果（query=" + query + "）。");
        }
        String summary = content.length() > 120 ? content.substring(0, 120) + "…" : content;
        return AgentToolResult.builder(content)
                .uiSummary(summary)
                .build();
    }
}
