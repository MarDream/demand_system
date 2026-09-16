package com.demand.system.module.nl2sql.dto;

import com.demand.system.module.assistant.dto.AssistantPageContext;
import com.demand.system.module.knowledge.dto.ConversationTurn;

import java.util.List;

/**
 * NL2SQL 执行上下文（由助手层组装）。
 *
 * @param question     用户问题（已做查询改写）
 * @param history      多轮对话历史（旧→新）
 * @param llmModelId   指定模型 ID（可为 null，走默认解析）
 * @param userId       当前用户 ID
 * @param superAdmin   是否超级管理员（决定是否注入组织数据范围）
 * @param visibleOrgIds 当前用户可见组织 ID 列表（非超管时用于行级过滤）
 * @param pageContext  当前页面上下文（用于消解"当前项目/当前需求"等指代）
 */
public record Nl2SqlContext(
        String question,
        List<ConversationTurn> history,
        Long llmModelId,
        Long userId,
        boolean superAdmin,
        List<Long> visibleOrgIds,
        AssistantPageContext pageContext
) {
}
