package com.demand.system.module.assistant.agent.tools;

import com.demand.system.module.knowledge.dto.KnowledgeSearchResponse;

/** 知识检索类工具共用的片段摘要构造 */
final class SearchDigestSupport {

    private SearchDigestSupport() {}

    /** 拼接命中片段摘要：《文档》· 小节：内容截断 */
    static String buildItemsDigest(KnowledgeSearchResponse response, int limit, int contentCap) {
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (KnowledgeSearchResponse.SearchResultItem item : response.getResults()) {
            if (count >= limit) {
                break;
            }
            count++;
            String name = item.getFileName() != null ? item.getFileName() : "未知文档";
            sb.append(count).append(". 《").append(name).append("》");
            if (item.getSectionTitle() != null && !item.getSectionTitle().isBlank()) {
                sb.append(" · ").append(item.getSectionTitle());
            }
            sb.append('\n');
            String content = item.getContent() == null ? "" : item.getContent();
            if (content.length() > contentCap) {
                content = content.substring(0, contentCap) + "…";
            }
            sb.append(content).append('\n');
        }
        return sb.toString();
    }
}
