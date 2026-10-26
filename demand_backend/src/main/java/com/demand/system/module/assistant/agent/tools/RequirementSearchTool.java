package com.demand.system.module.assistant.agent.tools;

import com.demand.system.module.assistant.agent.core.AgentTool;
import com.demand.system.module.assistant.agent.core.AgentToolContext;
import com.demand.system.module.assistant.agent.core.AgentToolResult;
import com.demand.system.module.assistant.agent.core.ToolParamSchema;
import com.demand.system.module.knowledge.constant.KnowledgeSearchScope;
import com.demand.system.module.knowledge.dto.KnowledgeSearchRequest;
import com.demand.system.module.knowledge.dto.KnowledgeSearchResponse;
import com.demand.system.module.knowledge.service.KnowledgeSearchService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** 工单正文检索工具：在需求/工单正文中做语义检索，返回相关工单片段 */
@Component
public class RequirementSearchTool implements AgentTool {

    private final KnowledgeSearchService knowledgeSearchService;

    public RequirementSearchTool(KnowledgeSearchService knowledgeSearchService) {
        this.knowledgeSearchService = knowledgeSearchService;
    }

    @Override
    public String name() {
        return "requirement_search";
    }

    @Override
    public String title() {
        return "工单检索";
    }

    @Override
    public String description() {
        return "在需求工单的正文描述中做语义检索，返回最相关的工单片段（含工单编号与标题）。"
                + "适合「之前有没有类似的工单/谁提过什么需求」类问题。";
    }

    @Override
    public Map<String, Object> schema() {
        return ToolParamSchema.object()
                .property("query", "string", "检索问题（针对工单内容的问句或关键词）")
                .property("topK", "integer", "返回片段数量，默认 5，最大 10")
                .required("query")
                .build();
    }

    @Override
    public AgentToolResult execute(AgentToolContext context, JsonNode args) throws Exception {
        String query = args.path("query").asText("");
        if (query.isBlank()) {
            return AgentToolResult.text("检索失败：query 参数不能为空");
        }
        int topK = args.path("topK").asInt(5);
        topK = Math.max(1, Math.min(topK, 10));

        KnowledgeSearchRequest request = new KnowledgeSearchRequest();
        request.setQuery(query);
        request.setKnowledgeBaseId(null);
        request.setMode("auto");
        request.setTopK(topK);
        request.setLlmModelId(context.getLlmModelId());
        request.setRequesterId(context.getUserId());
        request.setSearchScopes(List.of(KnowledgeSearchScope.REQUIREMENT_BODY));
        context.log("info", "工单正文检索：" + query + "（topK=" + topK + "）");

        KnowledgeSearchResponse response = knowledgeSearchService.retrieve(request);
        if (response.getResults() == null || response.getResults().isEmpty()) {
            return AgentToolResult.text("工单正文未命中相关内容（query=" + query + "）。");
        }
        int uniqueDocs = (int) response.getResults().stream()
                .map(KnowledgeSearchResponse.SearchResultItem::getDocumentId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .count();
        String digest = SearchDigestSupport.buildItemsDigest(response, topK, 400);
        AgentToolResult.Builder builder = AgentToolResult.builder(digest)
                .uiSummary("命中 " + response.getResults().size() + " 条工单片段（" + uniqueDocs + " 张工单）")
                .citations(response.getCitations());
        if (response.getWarnings() != null) {
            response.getWarnings().forEach(builder::warning);
        }
        return builder.build();
    }
}
