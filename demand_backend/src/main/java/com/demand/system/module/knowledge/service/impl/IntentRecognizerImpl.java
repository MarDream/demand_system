package com.demand.system.module.knowledge.service.impl;

import com.demand.system.module.knowledge.llm.LlmGateway;
import com.demand.system.module.knowledge.llm.LlmGatewayConfig;
import com.demand.system.module.knowledge.service.IntentRecognizer;
import com.demand.system.module.llm.entity.LlmModel;
import com.demand.system.module.llm.entity.LlmProvider;
import com.demand.system.module.llm.mapper.LlmModelMapper;
import com.demand.system.module.llm.mapper.LlmProviderMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 基于 LLM 的意图识别实现。
 * 通过调用 Chat 模型对用户问题分类，返回意图标签、置信度和归一化检索词。
 * 调用失败或无可用模型时降级为兜底结果。
 */
@Component
public class IntentRecognizerImpl implements IntentRecognizer {

    private static final Logger log = LoggerFactory.getLogger(IntentRecognizerImpl.class);

    private static final String INTENT_SYSTEM_PROMPT = """
            你是一个问题意图分类助手。分析用户的问题，输出一个意图标签和一个归一化检索词。

            可选的意图标签有：
            - 查询流程：询问审批流程、操作步骤、业务流程
            - 查阅文档：查阅规范、标准、制度文档
            - 统计信息：数据统计、报表、汇总
            - 故障排查：问题报错、异常排查、故障处理
            - 需求查询：查找需求详情、需求状态
            - 通用问答：不属于以上任何类别

            请严格按以下 JSON 格式返回，不要输出其他内容：
            {"intent":"查询流程","confidence":0.92,"normalizedQuery":"审批流程如何走"}
            """;

    private static final IntentResult FALLBACK = new IntentResult("通用问答", 0.5, null);

    private final LlmGateway llmGateway;
    private final LlmModelMapper llmModelMapper;
    private final LlmProviderMapper llmProviderMapper;
    private final ObjectMapper objectMapper;

    public IntentRecognizerImpl(LlmGateway llmGateway,
                                LlmModelMapper llmModelMapper,
                                LlmProviderMapper llmProviderMapper,
                                ObjectMapper objectMapper) {
        this.llmGateway = llmGateway;
        this.llmModelMapper = llmModelMapper;
        this.llmProviderMapper = llmProviderMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public IntentResult recognize(String userQuery) {
        LlmGatewayConfig.Provider provider = resolveProvider();
        if (provider == null) {
            log.warn("意图识别: 无可用 Chat 模型，使用兜底分类");
            return FALLBACK;
        }

        try {
            LlmGateway.ChatResult result = llmGateway.chatWithProvider(
                    provider, INTENT_SYSTEM_PROMPT, userQuery);
            return parseResult(result.getContent(), userQuery);
        } catch (Exception e) {
            log.warn("意图识别 LLM 调用失败，使用兜底分类: {}", e.getMessage());
            return FALLBACK;
        }
    }

    private IntentResult parseResult(String llmContent, String userQuery) {
        if (llmContent == null || llmContent.isBlank()) {
            return FALLBACK;
        }

        try {
            // 尝试从 LLM 返回中提取 JSON（可能被 markdown 包裹）
            String json = extractJson(llmContent);
            if (json == null) {
                return FALLBACK;
            }

            JsonNode root = objectMapper.readTree(json);
            String intent = root.has("intent") ? root.get("intent").asText() : null;
            double confidence = root.has("confidence") ? root.get("confidence").asDouble() : 0.5;
            String normalizedQuery = root.has("normalizedQuery") ? root.get("normalizedQuery").asText() : null;

            if (intent == null || intent.isBlank()) {
                return FALLBACK;
            }

            return new IntentResult(intent, confidence, normalizedQuery);
        } catch (Exception e) {
            log.warn("意图识别 JSON 解析失败: content={}", llmContent, e);
            return FALLBACK;
        }
    }

    private String extractJson(String text) {
        // 尝试直接解析
        String trimmed = text.trim();
        if (trimmed.startsWith("{")) {
            int end = trimmed.lastIndexOf("}");
            if (end > 0) {
                return trimmed.substring(0, end + 1);
            }
        }

        // 尝试从 markdown 代码块中提取
        int blockStart = trimmed.indexOf("```json");
        if (blockStart >= 0) {
            int contentStart = blockStart + "```json".length();
            int blockEnd = trimmed.indexOf("```", contentStart);
            if (blockEnd > contentStart) {
                return trimmed.substring(contentStart, blockEnd).trim();
            }
        }

        // 尝试提取第一个 { ... }
        int braceStart = trimmed.indexOf('{');
        int braceEnd = trimmed.lastIndexOf('}');
        if (braceStart >= 0 && braceEnd > braceStart) {
            return trimmed.substring(braceStart, braceEnd + 1);
        }

        return null;
    }

    private LlmGatewayConfig.Provider resolveProvider() {
        // 取一个启用的 Chat 模型用于意图识别
        List<LlmModel> models = llmModelMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<LlmModel>()
                        .eq(LlmModel::getEnabled, true)
                        .notIn(LlmModel::getModelType, "embedding", "rerank")
                        .orderByDesc(LlmModel::getIsDefault)
                        .last("LIMIT 1")
        );

        if (models.isEmpty()) {
            return null;
        }

        LlmModel model = models.get(0);
        LlmProvider provider = llmProviderMapper.selectById(model.getProviderId());
        if (provider == null || !Boolean.TRUE.equals(provider.getEnabled())) {
            return null;
        }

        var chatProvider = new LlmGatewayConfig.Provider();
        chatProvider.setProtocol(provider.getProtocol());
        chatProvider.setBaseUrl(provider.getBaseUrl());
        chatProvider.setApiKey(provider.getApiKey());
        chatProvider.setModel(model.getModelId());
        return chatProvider;
    }
}
