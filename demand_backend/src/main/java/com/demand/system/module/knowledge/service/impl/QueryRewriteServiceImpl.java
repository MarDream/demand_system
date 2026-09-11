package com.demand.system.module.knowledge.service.impl;

import com.demand.system.module.knowledge.dto.ConversationTurn;
import com.demand.system.module.knowledge.dto.QueryRewriteResult;
import com.demand.system.module.knowledge.llm.LlmGateway;
import com.demand.system.module.knowledge.llm.LlmGatewayConfig;
import com.demand.system.module.knowledge.service.QueryRewriteService;
import com.demand.system.module.knowledge.util.LlmJsonExtractor;
import com.demand.system.module.llm.constant.LlmApplicationCode;
import com.demand.system.module.llm.service.LlmModelResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 基于 LLM 的查询改写实现：一次调用同时产出独立检索问题、关键词、意图与追问推荐。
 * 无可用模型或调用失败时静默降级（{@link QueryRewriteResult#fallback}），不阻塞检索主流程。
 */
@Service
public class QueryRewriteServiceImpl implements QueryRewriteService {

    private static final Logger log = LoggerFactory.getLogger(QueryRewriteServiceImpl.class);

    private static final String SYSTEM_PROMPT = """
            你是企业知识库检索的查询优化助手。根据对话历史和当前问题，输出一个 JSON 对象：
            {"standaloneQuery":"...","keywords":["..."],"intent":"...","confidence":0.9,"followUps":["..."]}

            字段要求：
            1. standaloneQuery：把当前问题改写为不依赖对话历史、可独立用于知识库检索的完整问题。当前问题没有指代时保持原意微调即可，不要添加对话中不存在的信息。
            2. keywords：用于关键词检索的核心词，2~4 个，保留编号、型号、错误码等精确标识。
            3. intent：从「查询流程、查阅文档、统计信息、故障排查、需求查询、通用问答」中选一个。
            4. confidence：意图置信度，0~1。
            5. followUps：基于当前问题推荐 2~3 个用户最可能继续追问的问题，简短具体。
            严格只输出 JSON，不要输出任何其他内容。
            """;

    private static final int MAX_HISTORY_TURNS = 6;
    private static final int MAX_TURN_CHARS = 500;

    private final LlmGateway llmGateway;
    private final LlmModelResolver llmModelResolver;
    private final ObjectMapper objectMapper;

    public QueryRewriteServiceImpl(LlmGateway llmGateway,
                                   LlmModelResolver llmModelResolver,
                                   ObjectMapper objectMapper) {
        this.llmGateway = llmGateway;
        this.llmModelResolver = llmModelResolver;
        this.objectMapper = objectMapper;
    }

    @Override
    public QueryRewriteResult rewrite(String query, List<ConversationTurn> history) {
        LlmGatewayConfig.Provider provider = resolveProvider();
        if (provider == null) {
            return QueryRewriteResult.fallback(query);
        }
        try {
            String userMessage = buildUserMessage(query, history);
            LlmGateway.ChatResult result = llmGateway.chatWithProvider(provider, SYSTEM_PROMPT, userMessage);
            QueryRewriteResult parsed = parseResult(result != null ? result.getContent() : null, query);
            if (parsed != null) {
                return parsed;
            }
            return QueryRewriteResult.fallback(query);
        } catch (Exception e) {
            log.warn("查询改写失败，使用降级结果: {}", e.getMessage());
            return QueryRewriteResult.fallback(query);
        }
    }

    private String buildUserMessage(String query, List<ConversationTurn> history) {
        StringBuilder sb = new StringBuilder();
        if (history != null && !history.isEmpty()) {
            sb.append("【对话历史（旧→新）】\n");
            history.stream()
                    .limit(MAX_HISTORY_TURNS)
                    .forEach(turn -> sb.append("user".equals(turn.role()) ? "用户：" : "助手：")
                            .append(abbreviate(turn.safeContent()))
                            .append('\n'));
        }
        sb.append("【当前问题】\n").append(query);
        return sb.toString();
    }

    private QueryRewriteResult parseResult(String llmContent, String originalQuery) {
        RawRewrite raw = LlmJsonExtractor.parse(objectMapper, llmContent, RawRewrite.class);
        if (raw == null || raw.standaloneQuery == null || raw.standaloneQuery.isBlank()) {
            return null;
        }
        List<String> keywords = raw.keywords == null ? List.of()
                : raw.keywords.stream().filter(k -> k != null && !k.isBlank()).limit(5).toList();
        List<String> followUps = raw.followUps == null ? List.of()
                : raw.followUps.stream()
                        .filter(f -> f != null && !f.isBlank())
                        .map(f -> f.length() > 60 ? f.substring(0, 60) : f)
                        .limit(3)
                        .toList();
        return new QueryRewriteResult(
                raw.standaloneQuery.trim(),
                keywords,
                raw.intent == null || raw.intent.isBlank() ? "通用问答" : raw.intent.trim(),
                raw.confidence > 0 ? Math.min(raw.confidence, 1.0) : 0.5,
                followUps,
                true
        );
    }

    private String abbreviate(String text) {
        String compact = text.replaceAll("\\s+", " ").trim();
        return compact.length() <= MAX_TURN_CHARS ? compact : compact.substring(0, MAX_TURN_CHARS) + "…";
    }

    private LlmGatewayConfig.Provider resolveProvider() {
        LlmModelResolver.ResolvedModel resolved = llmModelResolver.resolveFirst(LlmApplicationCode.KNOWLEDGE_INTENT);
        return resolved == null ? null : llmModelResolver.toGatewayProvider(resolved);
    }

    /** LLM JSON 输出的原始载体 */
    public static class RawRewrite {
        public String standaloneQuery;
        public List<String> keywords;
        public String intent;
        public double confidence;
        public List<String> followUps;
    }
}
