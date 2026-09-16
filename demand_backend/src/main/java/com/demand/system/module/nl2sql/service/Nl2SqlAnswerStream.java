package com.demand.system.module.nl2sql.service;

import com.demand.system.module.knowledge.llm.LlmGateway;
import com.demand.system.module.nl2sql.dto.DataQueryResult;

/**
 * 回答流式回调：由助手层实现，把模型生成的 token 转成 SSE 事件。
 */
public interface Nl2SqlAnswerStream {

    /**
     * 结构化结果就绪（在自然语言回答开始流式输出之前回调），
     * 便于前端先渲染表格与图表，再逐字显示总结。
     */
    default void onDataResult(DataQueryResult result) {
    }

    /** 正文增量 */
    void onToken(String token);

    /** 深度思考增量（可为 null 表示不关心） */
    default void onReasoning(String token) {
    }

    /** token 用量统计 */
    default void onUsage(LlmGateway.ChatUsage usage) {
    }
}
