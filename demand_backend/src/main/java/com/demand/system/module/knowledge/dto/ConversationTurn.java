package com.demand.system.module.knowledge.dto;

/**
 * 对话历史中的一轮发言，用于多轮场景下的查询改写与回答上下文。
 *
 * @param role    角色：user / assistant
 * @param content 发言内容
 */
public record ConversationTurn(String role, String content) {

    public static ConversationTurn of(String role, String content) {
        return new ConversationTurn(role, content);
    }

    public String safeContent() {
        return content == null ? "" : content.trim();
    }
}
