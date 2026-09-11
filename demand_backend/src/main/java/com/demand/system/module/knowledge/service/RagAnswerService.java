package com.demand.system.module.knowledge.service;

import com.demand.system.module.knowledge.dto.ConversationTurn;
import com.demand.system.module.knowledge.dto.KnowledgeSearchResponse;
import com.demand.system.module.knowledge.llm.LlmGateway;
import com.demand.system.module.knowledge.llm.LlmGateway.ChatUsage;

import java.util.List;
import java.util.function.Consumer;

public interface RagAnswerService {

    /**
     * 非流式生成答案（含深度思考 reasoning 内容）。
     *
     * @param history 对话历史，用于多轮场景下保持回答连贯；可为空
     */
    LlmGateway.ChatResult generateAnswerWithReasoning(
            String query,
            List<KnowledgeSearchResponse.SearchResultItem> searchResults,
            Long knowledgeBaseId,
            Long llmModelId,
            List<ConversationTurn> history
    );

    /**
     * 流式生成答案：token、深度思考内容与真实 token 用量分别通过独立回调推送。
     * <p>深度思考参数不被端点支持且尚无任何输出时，自动降级为普通模式重试一次。</p>
     *
     * @param history           对话历史，可为空
     * @param reasoningConsumer 深度思考增量回调，可为 null
     * @param usageConsumer     流结束后的 token 用量回调，可为 null
     */
    void streamAnswerWithReasoning(
            String query,
            List<KnowledgeSearchResponse.SearchResultItem> searchResults,
            Long knowledgeBaseId,
            Long llmModelId,
            List<ConversationTurn> history,
            Consumer<String> tokenConsumer,
            Consumer<String> reasoningConsumer,
            Consumer<ChatUsage> usageConsumer
    );
}
