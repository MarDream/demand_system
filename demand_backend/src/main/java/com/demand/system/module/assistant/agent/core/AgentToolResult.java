package com.demand.system.module.assistant.agent.core;

import com.demand.system.module.assistant.dto.AssistantSource;
import com.demand.system.module.knowledge.dto.KnowledgeSearchResponse;
import com.demand.system.module.nl2sql.dto.DataQueryResult;

import java.util.ArrayList;
import java.util.List;

/** 工具执行结果：resultForLlm 回传模型，其余并入消息证据供前端展示与综合层引用 */
public class AgentToolResult {

    /** 回传给模型的文本（必填，空串表示无产出） */
    private final String resultForLlm;
    /** 工具卡片结果摘要（空则截取 resultForLlm） */
    private final String uiSummary;
    private final List<AssistantSource> sources;
    private final List<KnowledgeSearchResponse.CitationReference> citations;
    private final DataQueryResult dataResult;
    private final List<String> warnings;

    private AgentToolResult(String resultForLlm, String uiSummary,
                            List<AssistantSource> sources,
                            List<KnowledgeSearchResponse.CitationReference> citations,
                            DataQueryResult dataResult,
                            List<String> warnings) {
        this.resultForLlm = resultForLlm;
        this.uiSummary = uiSummary;
        this.sources = sources;
        this.citations = citations;
        this.dataResult = dataResult;
        this.warnings = warnings;
    }

    public static AgentToolResult text(String resultForLlm) {
        return new AgentToolResult(resultForLlm, null, List.of(), List.of(), null, List.of());
    }

    public static Builder builder(String resultForLlm) {
        return new Builder(resultForLlm);
    }

    public String getResultForLlm() { return resultForLlm; }

    public String getUiSummary() { return uiSummary; }

    public List<AssistantSource> getSources() { return sources; }

    public List<KnowledgeSearchResponse.CitationReference> getCitations() { return citations; }

    public DataQueryResult getDataResult() { return dataResult; }

    public List<String> getWarnings() { return warnings; }

    public static class Builder {
        private final String resultForLlm;
        private String uiSummary;
        private final List<AssistantSource> sources = new ArrayList<>();
        private final List<KnowledgeSearchResponse.CitationReference> citations = new ArrayList<>();
        private DataQueryResult dataResult;
        private final List<String> warnings = new ArrayList<>();

        private Builder(String resultForLlm) {
            this.resultForLlm = resultForLlm;
        }

        public Builder uiSummary(String uiSummary) {
            this.uiSummary = uiSummary;
            return this;
        }

        public Builder sources(List<AssistantSource> sources) {
            if (sources != null) {
                this.sources.addAll(sources);
            }
            return this;
        }

        public Builder citations(List<KnowledgeSearchResponse.CitationReference> citations) {
            if (citations != null) {
                this.citations.addAll(citations);
            }
            return this;
        }

        public Builder dataResult(DataQueryResult dataResult) {
            this.dataResult = dataResult;
            return this;
        }

        public Builder warning(String warning) {
            if (warning != null && !warning.isBlank()) {
                this.warnings.add(warning);
            }
            return this;
        }

        public AgentToolResult build() {
            return new AgentToolResult(resultForLlm, uiSummary, sources, citations, dataResult, warnings);
        }
    }
}
