package com.demand.system.module.knowledge.service;

import com.demand.system.module.knowledge.dto.KnowledgeSearchRequest;
import com.demand.system.module.knowledge.dto.KnowledgeSearchResponse;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

public interface KnowledgeSearchService {

    /** 检索 + （按条件）生成答案，非流式，供 REST 检索接口使用 */
    KnowledgeSearchResponse search(KnowledgeSearchRequest request);

    /**
     * 仅检索不生成：权限过滤、来源引用与提示信息组装齐全，
     * 供 AI 助手等需要自行编排流式生成的调用方复用。
     */
    KnowledgeSearchResponse retrieve(KnowledgeSearchRequest request);

    /** 检索 + 流式生成答案（SSE：results / delta / reasoningDelta / usage / done） */
    SseEmitter streamSearch(KnowledgeSearchRequest request);

    /**
     * 从检索结果中挑选真正进入 LLM 上下文的片段（分数下限 + 字符预算裁剪）。
     * 返回的 citations 正是基于这份裁剪结果编号的：自行编排生成的调用方
     * （如 AI 助手）必须把回答生成的资料也换成这里的结果，
     * 否则提示词里的资料 [N] 会超出 citations 范围，前端角标在引用来源里找不到条目。
     */
    List<KnowledgeSearchResponse.SearchResultItem> selectContextResults(
            List<KnowledgeSearchResponse.SearchResultItem> results);
}
