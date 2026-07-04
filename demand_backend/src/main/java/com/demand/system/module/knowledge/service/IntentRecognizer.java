package com.demand.system.module.knowledge.service;

/**
 * 用户问题意图识别接口。
 * 在知识库语义检索之前，调用 LLM 对用户提问做意图分类，
 * 使后续检索和回答可针对意图调整策略。
 */
public interface IntentRecognizer {

    /**
     * 意图识别结果
     */
    record IntentResult(
            String intent,
            Double confidence,
            String normalizedQuery
    ) {}

    /**
     * 对用户问题进行意图识别。
     *
     * @param userQuery 用户原始提问
     * @return 意图识别结果；若无法识别（如无可用 LLM）返回兜底结果
     */
    IntentResult recognize(String userQuery);
}
