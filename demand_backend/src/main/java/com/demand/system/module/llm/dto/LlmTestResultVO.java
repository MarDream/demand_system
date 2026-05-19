package com.demand.system.module.llm.dto;

public class LlmTestResultVO {
    private boolean success;
    private String content;
    private String errorMessage;
    private long durationMs;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private String model;

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
    public Integer getPromptTokens() { return promptTokens; }
    public void setPromptTokens(Integer promptTokens) { this.promptTokens = promptTokens; }
    public Integer getCompletionTokens() { return completionTokens; }
    public void setCompletionTokens(Integer completionTokens) { this.completionTokens = completionTokens; }
    public Integer getTotalTokens() { return totalTokens; }
    public void setTotalTokens(Integer totalTokens) { this.totalTokens = totalTokens; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private boolean success;
        private String content;
        private String errorMessage;
        private long durationMs;
        private Integer promptTokens;
        private Integer completionTokens;
        private Integer totalTokens;
        private String model;

        public Builder success(boolean success) { this.success = success; return this; }
        public Builder content(String content) { this.content = content; return this; }
        public Builder errorMessage(String errorMessage) { this.errorMessage = errorMessage; return this; }
        public Builder durationMs(long durationMs) { this.durationMs = durationMs; return this; }
        public Builder promptTokens(Integer promptTokens) { this.promptTokens = promptTokens; return this; }
        public Builder completionTokens(Integer completionTokens) { this.completionTokens = completionTokens; return this; }
        public Builder totalTokens(Integer totalTokens) { this.totalTokens = totalTokens; return this; }
        public Builder model(String model) { this.model = model; return this; }
        public LlmTestResultVO build() {
            LlmTestResultVO vo = new LlmTestResultVO();
            vo.success = this.success;
            vo.content = this.content;
            vo.errorMessage = this.errorMessage;
            vo.durationMs = this.durationMs;
            vo.promptTokens = this.promptTokens;
            vo.completionTokens = this.completionTokens;
            vo.totalTokens = this.totalTokens;
            vo.model = this.model;
            return vo;
        }
    }
}
