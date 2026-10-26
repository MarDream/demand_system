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

/** 知识库文档检索工具：跨全部知识库做混合检索，命中片段与角标来源回传 */
@Component
public class KnowledgeSearchTool implements AgentTool {

    private final KnowledgeSearchService knowledgeSearchService;

    public KnowledgeSearchTool(KnowledgeSearchService knowledgeSearchService) {
        this.knowledgeSearchService = knowledgeSearchService;
    }

    @Override
    public String name() {
        return "knowledge_search";
    }

    @Override
    public String title() {
        return "知识库检索";
    }

    @Override
    public String description() {
        return "检索企业知识库文档（制度规范、操作手册、流程说明等），返回最相关的文档片段。"
                + "适合回答「怎么做/有什么规定/流程是什么」类问题。";
    }

    @Override
    public Map<String, Object> schema() {
        return ToolParamSchema.object()
                .property("query", "string", "检索问题（独立、完整的问句或关键词）")
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
        request.setSearchScopes(List.of(KnowledgeSearchScope.KNOWLEDGE_BASE));
        context.log("info", "知识库检索：" + query + "（topK=" + topK + "）");

        KnowledgeSearchResponse response = knowledgeSearchService.retrieve(request);
        if (response.getResults() == null || response.getResults().isEmpty()) {
            return AgentToolResult.text("知识库未命中相关内容（query=" + query + "）。");
        }
        int uniqueDocs = (int) response.getResults().stream()
                .map(KnowledgeSearchResponse.SearchResultItem::getDocumentId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .count();
        String digest = SearchDigestSupport.buildItemsDigest(response, topK, 400);
        AgentToolResult.Builder builder = AgentToolResult.builder(digest)
                .uiSummary("命中 " + response.getResults().size() + " 条片段（" + uniqueDocs + " 份文档）")
                .citations(response.getCitations());
        if (response.getWarnings() != null) {
            response.getWarnings().forEach(builder::warning);
        }
        return builder.build();
    }
}
