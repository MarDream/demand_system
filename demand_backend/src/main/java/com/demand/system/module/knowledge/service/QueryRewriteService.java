package com.demand.system.module.knowledge.service;

import com.demand.system.module.knowledge.dto.ConversationTurn;
import com.demand.system.module.knowledge.dto.QueryRewriteResult;

import java.util.List;

/**
 * 检索查询改写服务：结合对话历史把当前问题改写为可独立检索的问题，
 * 同时产出关键词、意图标签与推荐追问，一次 LLM 调用全部完成。
 */
public interface QueryRewriteService {

    /**
     * @param query   当前用户问题
     * @param history 最近的对话历史（可 为空；为空时仅做关键词与追问提炼）
     * @return 改写结果；LLM 调用失败或无可用模型时返回降级结果（{@link QueryRewriteResult#fallback}）
     */
    QueryRewriteResult rewrite(String query, List<ConversationTurn> history);
}
