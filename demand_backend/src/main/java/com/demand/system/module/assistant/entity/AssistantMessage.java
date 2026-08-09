package com.demand.system.module.assistant.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.demand.system.module.assistant.dto.AssistantAction;
import com.demand.system.module.assistant.dto.AssistantPageContext;
import com.demand.system.module.assistant.dto.AssistantSource;
import com.demand.system.module.assistant.dto.AssistantTask;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import com.demand.system.module.knowledge.dto.KnowledgeSearchResponse.CitationReference;
import com.demand.system.module.knowledge.dto.KnowledgeSearchResponse.ThinkingStep;

@TableName(value = "assistant_messages", autoResultMap = true)
public class AssistantMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sessionId;

    private Long userId;

    private String role;

    private String content;

    private String status;

    private String intent;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private AssistantPageContext pageContext;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<AssistantAction> actions;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<AssistantSource> sources;

    /** 思维链步骤（RAG 问答时填充） */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<ThinkingStep> thinkingSteps;

    /** 任务列表（检索过程，对标 WorkBuddy 任务列表） */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<AssistantTask> tasks;

    /** 检索过程摘要（RAG 问答时填充） */
    private String processSummary;

    /** 命中的片段数量（RAG 问答时填充） */
    private Integer retrievedCount;

    /** 引用文档列表（RAG 问答时填充） */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<CitationReference> citations;

    /** 检索降级与能力提示（例如未配置 vision 模型） */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> warnings;

    /** 深度思考内容（LLM reasoning，可为 null） */
    private String reasoning;

    /** 输入（提示词）token 数，由 LLM usage 统计 */
    private Integer inputTokens;

    /** 输出（生成）token 数，由 LLM usage 统计 */
    private Integer outputTokens;

    /** 总 token 数 */
    private Integer totalTokens;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deletedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getIntent() {
        return intent;
    }

    public void setIntent(String intent) {
        this.intent = intent;
    }

    public AssistantPageContext getPageContext() {
        return pageContext;
    }

    public void setPageContext(AssistantPageContext pageContext) {
        this.pageContext = pageContext;
    }

    public List<AssistantAction> getActions() {
        return actions;
    }

    public void setActions(List<AssistantAction> actions) {
        this.actions = actions;
    }

    public List<AssistantSource> getSources() {
        return sources;
    }

    public void setSources(List<AssistantSource> sources) {
        this.sources = sources;
    }

    public List<ThinkingStep> getThinkingSteps() {
        return thinkingSteps;
    }

    public void setThinkingSteps(List<ThinkingStep> thinkingSteps) {
        this.thinkingSteps = thinkingSteps;
    }

    public List<AssistantTask> getTasks() {
        return tasks;
    }

    public void setTasks(List<AssistantTask> tasks) {
        this.tasks = tasks;
    }

    public String getProcessSummary() {
        return processSummary;
    }

    public void setProcessSummary(String processSummary) {
        this.processSummary = processSummary;
    }

    public Integer getRetrievedCount() {
        return retrievedCount;
    }

    public void setRetrievedCount(Integer retrievedCount) {
        this.retrievedCount = retrievedCount;
    }

    public List<CitationReference> getCitations() {
        return citations;
    }

    public void setCitations(List<CitationReference> citations) {
        this.citations = citations;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings;
    }

    public String getReasoning() {
        return reasoning;
    }

    public void setReasoning(String reasoning) {
        this.reasoning = reasoning;
    }

    public Integer getInputTokens() {
        return inputTokens;
    }

    public void setInputTokens(Integer inputTokens) {
        this.inputTokens = inputTokens;
    }

    public Integer getOutputTokens() {
        return outputTokens;
    }

    public void setOutputTokens(Integer outputTokens) {
        this.outputTokens = outputTokens;
    }

    public Integer getTotalTokens() {
        return totalTokens;
    }

    public void setTotalTokens(Integer totalTokens) {
        this.totalTokens = totalTokens;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Integer getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Integer deletedAt) {
        this.deletedAt = deletedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AssistantMessage that = (AssistantMessage) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
