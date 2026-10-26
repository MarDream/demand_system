package com.demand.system.module.assistant.agent.core;

import com.demand.system.module.assistant.dto.AssistantPageContext;
import com.demand.system.module.knowledge.dto.ConversationTurn;
import com.demand.system.module.knowledge.llm.LlmGatewayConfig;

import java.util.List;
import java.util.function.BiConsumer;

/**
 * 工具执行上下文：派发前捕获的登录态与请求参数快照。
 * <p>工具循环运行在异步线程上，不能读取 SecurityUtils，
 * 所有身份信息在 SSE 派发点由调用方捕获后传入。</p>
 */
public class AgentToolContext {

    private final Long userId;
    private final boolean superAdmin;
    private final List<String> permissions;
    private final AssistantPageContext pageContext;
    private final List<ConversationTurn> history;
    /** 用户手选模型 ID（工具内部再调 LLM 时优先使用，如 NL2SQL） */
    private final Long llmModelId;
    /** 当前执行 Agent 所用的模型（联网搜索等需要自带 LLM 的工具使用） */
    private LlmGatewayConfig.Provider chatProvider;
    /** 过程日志回调（level: info/warn/error），写入任务面板 */
    private final BiConsumer<String, String> progressLogger;

    public AgentToolContext(Long userId,
                            boolean superAdmin,
                            List<String> permissions,
                            AssistantPageContext pageContext,
                            List<ConversationTurn> history,
                            Long llmModelId,
                            BiConsumer<String, String> progressLogger) {
        this.userId = userId;
        this.superAdmin = superAdmin;
        this.permissions = permissions;
        this.pageContext = pageContext;
        this.history = history;
        this.llmModelId = llmModelId;
        this.progressLogger = progressLogger;
    }

    public Long getUserId() { return userId; }

    public boolean isSuperAdmin() { return superAdmin; }

    public List<String> getPermissions() { return permissions; }

    public AssistantPageContext getPageContext() { return pageContext; }

    public List<ConversationTurn> getHistory() { return history; }

    public Long getLlmModelId() { return llmModelId; }

    public LlmGatewayConfig.Provider getChatProvider() { return chatProvider; }

    public void setChatProvider(LlmGatewayConfig.Provider chatProvider) {
        this.chatProvider = chatProvider;
    }

    public void log(String level, String message) {
        if (progressLogger != null) {
            progressLogger.accept(level, message);
        }
    }
}
