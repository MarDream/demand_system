package com.demand.system.module.assistant.dto;

import java.util.ArrayList;
import java.util.List;

public class AssistantOperationAdvice {

    private String intent;
    private String fallbackAnswer;
    private String sessionTitle;
    private List<AssistantAction> actions = new ArrayList<>();
    private List<AssistantSource> sources = new ArrayList<>();

    public String getIntent() {
        return intent;
    }

    public void setIntent(String intent) {
        this.intent = intent;
    }

    public String getFallbackAnswer() {
        return fallbackAnswer;
    }

    public void setFallbackAnswer(String fallbackAnswer) {
        this.fallbackAnswer = fallbackAnswer;
    }

    public String getSessionTitle() {
        return sessionTitle;
    }

    public void setSessionTitle(String sessionTitle) {
        this.sessionTitle = sessionTitle;
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
}
