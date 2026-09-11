package com.demand.system.module.knowledge.service;

import com.demand.system.module.knowledge.dto.KnowledgeSearchRequest;
import com.demand.system.module.knowledge.dto.KnowledgeSearchResponse;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

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
}
