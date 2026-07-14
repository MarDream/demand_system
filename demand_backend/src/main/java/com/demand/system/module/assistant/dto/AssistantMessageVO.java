package com.demand.system.module.assistant.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class AssistantMessageVO {

    private Long id;
    private Long sessionId;
    private String role;
    private String content;
    private String status;
    private String intent;
    private AssistantPageContext pageContext;
    private List<AssistantAction> actions = new ArrayList<>();
    private List<AssistantSource> sources = new ArrayList<>();
    private LocalDateTime createdAt;

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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
