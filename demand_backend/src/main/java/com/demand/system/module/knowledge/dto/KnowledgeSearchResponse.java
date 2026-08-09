package com.demand.system.module.knowledge.dto;

import java.util.List;
import java.util.Map;

public class KnowledgeSearchResponse {

    private List<SearchResultItem> results;
    private Integer total;
    private String answer;
    /** 深度思考内容（LLM reasoning，可为 null） */
    private String reasoningContent;
    private String processSummary;
    private List<ThinkingStep> thinkingSteps;
    private String questionIntent;
    private Double intentConfidence;
    private List<CitationReference> citations;
    private List<String> warnings;

    public List<SearchResultItem> getResults() {
        return results;
    }

    public void setResults(List<SearchResultItem> results) {
        this.results = results;
    }

    public Integer getTotal() {
        return total;
    }

    public void setTotal(Integer total) {
        this.total = total;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public String getReasoningContent() {
        return reasoningContent;
    }

    public void setReasoningContent(String reasoningContent) {
        this.reasoningContent = reasoningContent;
    }

    public String getProcessSummary() {
        return processSummary;
    }

    public void setProcessSummary(String processSummary) {
        this.processSummary = processSummary;
    }

    public List<ThinkingStep> getThinkingSteps() {
        return thinkingSteps;
    }

    public void setThinkingSteps(List<ThinkingStep> thinkingSteps) {
        this.thinkingSteps = thinkingSteps;
    }

    public String getQuestionIntent() {
        return questionIntent;
    }

    public void setQuestionIntent(String questionIntent) {
        this.questionIntent = questionIntent;
    }

    public Double getIntentConfidence() {
        return intentConfidence;
    }

    public void setIntentConfidence(Double intentConfidence) {
        this.intentConfidence = intentConfidence;
    }

    public List<CitationReference> getCitations() {
        return citations;
    }

    public void setCitations(List<CitationReference> citations) {
        this.citations = citations;
    }

    public List<String> getWarnings() { return warnings; }
    public void setWarnings(List<String> warnings) { this.warnings = warnings; }

    public static KnowledgeSearchResponseBuilder builder() {
        return new KnowledgeSearchResponseBuilder();
    }

    /**
     * 思维链步骤：
     * stepType 标识步骤类型，用于前端样式和交互：
     *   query_parse  - 问题解析
     *   retrieve     - 文档检索
     *   rerank       - 结果重排序
     *   synthesize   - 答案综合
     */
    public static class ThinkingStep {
        private String stepType;
        private String title;
        private String detail;
        private Double score;
        private Map<String, Object> metadata;

        public ThinkingStep() {}

        public ThinkingStep(String stepType, String title, String detail) {
            this.stepType = stepType;
            this.title = title;
            this.detail = detail;
        }

        public ThinkingStep(String stepType, String title, String detail, Double score) {
            this.stepType = stepType;
            this.title = title;
            this.detail = detail;
            this.score = score;
        }

        public String getStepType() { return stepType; }
        public void setStepType(String stepType) { this.stepType = stepType; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getDetail() { return detail; }
        public void setDetail(String detail) { this.detail = detail; }
        public Double getScore() { return score; }
        public void setScore(Double score) { this.score = score; }
        public Map<String, Object> getMetadata() { return metadata; }
        public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }

        public static Builder builder() { return new Builder(); }

        public static class Builder {
            private String stepType;
            private String title;
            private String detail;
            private Double score;
            private Map<String, Object> metadata;

            public Builder stepType(String v) { this.stepType = v; return this; }
            public Builder title(String v) { this.title = v; return this; }
            public Builder detail(String v) { this.detail = v; return this; }
            public Builder score(Double v) { this.score = v; return this; }
            public Builder metadata(Map<String, Object> v) { this.metadata = v; return this; }

            public ThinkingStep build() {
                ThinkingStep s = new ThinkingStep();
                s.setStepType(stepType);
                s.setTitle(title);
                s.setDetail(detail);
                s.setScore(score);
                s.setMetadata(metadata);
                return s;
            }
        }
    }

    public static class CitationReference {
        private Integer index;
        private Long documentId;
        private String fileName;
        private Integer hitCount;
        private Double maxScore;
        private List<String> sources;
        private String knowledgeBaseId;
        private String sourceType;
        private Long requirementId;
        private String requirementNo;
        private String requirementTitle;
        /** 命中内容类型：body、image_ocr、image_caption 或 body_image。 */
        private String contentType;
        private Long imageFileId;
        private Integer imagePosition;
        private String focus;

        public Integer getIndex() { return index; }
        public void setIndex(Integer index) { this.index = index; }
        public Long getDocumentId() { return documentId; }
        public void setDocumentId(Long documentId) { this.documentId = documentId; }
        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }
        public Integer getHitCount() { return hitCount; }
        public void setHitCount(Integer hitCount) { this.hitCount = hitCount; }
        public Double getMaxScore() { return maxScore; }
        public void setMaxScore(Double maxScore) { this.maxScore = maxScore; }
        public List<String> getSources() { return sources; }
        public void setSources(List<String> sources) { this.sources = sources; }
        public String getKnowledgeBaseId() { return knowledgeBaseId; }
        public void setKnowledgeBaseId(String knowledgeBaseId) { this.knowledgeBaseId = knowledgeBaseId; }
        public String getSourceType() { return sourceType; }
        public void setSourceType(String sourceType) { this.sourceType = sourceType; }
        public Long getRequirementId() { return requirementId; }
        public void setRequirementId(Long requirementId) { this.requirementId = requirementId; }
        public String getRequirementNo() { return requirementNo; }
        public void setRequirementNo(String requirementNo) { this.requirementNo = requirementNo; }
        public String getRequirementTitle() { return requirementTitle; }
        public void setRequirementTitle(String requirementTitle) { this.requirementTitle = requirementTitle; }
        public String getContentType() { return contentType; }
        public void setContentType(String contentType) { this.contentType = contentType; }
        public Long getImageFileId() { return imageFileId; }
        public void setImageFileId(Long imageFileId) { this.imageFileId = imageFileId; }
        public Integer getImagePosition() { return imagePosition; }
        public void setImagePosition(Integer imagePosition) { this.imagePosition = imagePosition; }
        public String getFocus() { return focus; }
        public void setFocus(String focus) { this.focus = focus; }

        public static Builder builder() { return new Builder(); }

        public static class Builder {
            private Integer index;
            private Long documentId;
            private String fileName;
            private Integer hitCount;
            private Double maxScore;
            private List<String> sources;
            private String knowledgeBaseId;
            private String sourceType;
            private Long requirementId;
            private String requirementNo;
            private String requirementTitle;
            private String contentType;
            private Long imageFileId;
            private Integer imagePosition;
            private String focus;

            public Builder index(Integer v) { this.index = v; return this; }
            public Builder documentId(Long v) { this.documentId = v; return this; }
            public Builder fileName(String v) { this.fileName = v; return this; }
            public Builder hitCount(Integer v) { this.hitCount = v; return this; }
            public Builder maxScore(Double v) { this.maxScore = v; return this; }
            public Builder sources(List<String> v) { this.sources = v; return this; }
            public Builder knowledgeBaseId(String v) { this.knowledgeBaseId = v; return this; }
            public Builder sourceType(String v) { this.sourceType = v; return this; }
            public Builder requirementId(Long v) { this.requirementId = v; return this; }
            public Builder requirementNo(String v) { this.requirementNo = v; return this; }
            public Builder requirementTitle(String v) { this.requirementTitle = v; return this; }
            public Builder contentType(String v) { this.contentType = v; return this; }
            public Builder imageFileId(Long v) { this.imageFileId = v; return this; }
            public Builder imagePosition(Integer v) { this.imagePosition = v; return this; }
            public Builder focus(String v) { this.focus = v; return this; }

            public CitationReference build() {
                CitationReference r = new CitationReference();
                r.setIndex(index);
                r.setDocumentId(documentId);
                r.setFileName(fileName);
                r.setHitCount(hitCount);
                r.setMaxScore(maxScore);
                r.setSources(sources);
                r.setKnowledgeBaseId(knowledgeBaseId);
                r.setSourceType(sourceType);
                r.setRequirementId(requirementId);
                r.setRequirementNo(requirementNo);
                r.setRequirementTitle(requirementTitle);
                r.setContentType(contentType);
                r.setImageFileId(imageFileId);
                r.setImagePosition(imagePosition);
                r.setFocus(focus);
                return r;
            }
        }
    }

    public static class KnowledgeSearchResponseBuilder {
        private List<SearchResultItem> results;
        private Integer total;
        private String answer;
        private String reasoningContent;
        private String processSummary;
        private List<ThinkingStep> thinkingSteps;
        private String questionIntent;
        private Double intentConfidence;
        private List<CitationReference> citations;
        private List<String> warnings;

        public KnowledgeSearchResponseBuilder results(List<SearchResultItem> results) {
            this.results = results;
            return this;
        }

        public KnowledgeSearchResponseBuilder total(Integer total) {
            this.total = total;
            return this;
        }

        public KnowledgeSearchResponseBuilder answer(String answer) {
            this.answer = answer;
            return this;
        }

        public KnowledgeSearchResponseBuilder reasoningContent(String reasoningContent) {
            this.reasoningContent = reasoningContent;
            return this;
        }

        public KnowledgeSearchResponseBuilder processSummary(String processSummary) {
            this.processSummary = processSummary;
            return this;
        }

        public KnowledgeSearchResponseBuilder thinkingSteps(List<ThinkingStep> steps) {
            this.thinkingSteps = steps;
            return this;
        }

        public KnowledgeSearchResponseBuilder questionIntent(String questionIntent) {
            this.questionIntent = questionIntent;
            return this;
        }

        public KnowledgeSearchResponseBuilder intentConfidence(Double intentConfidence) {
            this.intentConfidence = intentConfidence;
            return this;
        }

        public KnowledgeSearchResponseBuilder citations(List<CitationReference> citations) {
            this.citations = citations;
            return this;
        }

        public KnowledgeSearchResponseBuilder warnings(List<String> warnings) {
            this.warnings = warnings;
            return this;
        }

        public KnowledgeSearchResponse build() {
            KnowledgeSearchResponse response = new KnowledgeSearchResponse();
            response.setResults(results);
            response.setTotal(total);
            response.setAnswer(answer);
            response.setReasoningContent(reasoningContent);
            response.setProcessSummary(processSummary);
            response.setThinkingSteps(thinkingSteps);
            response.setQuestionIntent(questionIntent);
            response.setIntentConfidence(intentConfidence);
            response.setCitations(citations);
            response.setWarnings(warnings);
            return response;
        }
    }

    public static class SearchResultItem {
        private Long chunkId;
        private Long documentId;
        private String fileName;
        private String sectionTitle;
        private String content;
        private Integer pageNum;
        private Double score;
        private String knowledgeBaseId;
        private RequirementReference requirement;
        private Long imageFileId;
        private Integer imagePosition;
        private String focus;

        public Long getChunkId() {
            return chunkId;
        }

        public void setChunkId(Long chunkId) {
            this.chunkId = chunkId;
        }

        public Long getDocumentId() {
            return documentId;
        }

        public void setDocumentId(Long documentId) {
            this.documentId = documentId;
        }

        public String getFileName() {
            return fileName;
        }

        public void setFileName(String fileName) {
            this.fileName = fileName;
        }

        public String getSectionTitle() {
            return sectionTitle;
        }

        public void setSectionTitle(String sectionTitle) {
            this.sectionTitle = sectionTitle;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public Integer getPageNum() {
            return pageNum;
        }

        public void setPageNum(Integer pageNum) {
            this.pageNum = pageNum;
        }

        public Double getScore() {
            return score;
        }

        public void setScore(Double score) {
            this.score = score;
        }

        public String getKnowledgeBaseId() {
            return knowledgeBaseId;
        }

        public void setKnowledgeBaseId(String knowledgeBaseId) {
            this.knowledgeBaseId = knowledgeBaseId;
        }

        public RequirementReference getRequirement() {
            return requirement;
        }

        public void setRequirement(RequirementReference requirement) {
            this.requirement = requirement;
        }
        public Long getImageFileId() { return imageFileId; }
        public void setImageFileId(Long imageFileId) { this.imageFileId = imageFileId; }
        public Integer getImagePosition() { return imagePosition; }
        public void setImagePosition(Integer imagePosition) { this.imagePosition = imagePosition; }
        public String getFocus() { return focus; }
        public void setFocus(String focus) { this.focus = focus; }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private Long chunkId;
            private Long documentId;
            private String fileName;
            private String sectionTitle;
            private String content;
            private Integer pageNum;
            private Double score;
            private String knowledgeBaseId;
            private RequirementReference requirement;
            private Long imageFileId;
            private Integer imagePosition;
            private String focus;

            public Builder chunkId(Long chunkId) {
                this.chunkId = chunkId;
                return this;
            }

            public Builder documentId(Long documentId) {
                this.documentId = documentId;
                return this;
            }

            public Builder fileName(String fileName) {
                this.fileName = fileName;
                return this;
            }

            public Builder sectionTitle(String sectionTitle) {
                this.sectionTitle = sectionTitle;
                return this;
            }

            public Builder content(String content) {
                this.content = content;
                return this;
            }

            public Builder pageNum(Integer pageNum) {
                this.pageNum = pageNum;
                return this;
            }

            public Builder score(Double score) {
                this.score = score;
                return this;
            }

            public Builder knowledgeBaseId(String knowledgeBaseId) {
                this.knowledgeBaseId = knowledgeBaseId;
                return this;
            }

            public Builder requirement(RequirementReference requirement) {
                this.requirement = requirement;
                return this;
            }

            public Builder imageFileId(Long v) { this.imageFileId = v; return this; }
            public Builder imagePosition(Integer v) { this.imagePosition = v; return this; }
            public Builder focus(String v) { this.focus = v; return this; }

            public SearchResultItem build() {
                SearchResultItem item = new SearchResultItem();
                item.setChunkId(chunkId);
                item.setDocumentId(documentId);
                item.setFileName(fileName);
                item.setSectionTitle(sectionTitle);
                item.setContent(content);
                item.setPageNum(pageNum);
                item.setScore(score);
                item.setKnowledgeBaseId(knowledgeBaseId);
                item.setRequirement(requirement);
                item.setImageFileId(imageFileId);
                item.setImagePosition(imagePosition);
                item.setFocus(focus);
                return item;
            }
        }
    }

    public static class RequirementReference {
        private Long id;
        private String title;
        private String status;
        private String type;
        private String summary;
        private String requirementNo;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getSummary() {
            return summary;
        }

        public void setSummary(String summary) {
            this.summary = summary;
        }

        public String getRequirementNo() {
            return requirementNo;
        }

        public void setRequirementNo(String requirementNo) {
            this.requirementNo = requirementNo;
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private Long id;
            private String title;
            private String status;
            private String type;
            private String summary;
            private String requirementNo;

            public Builder id(Long id) {
                this.id = id;
                return this;
            }

            public Builder title(String title) {
                this.title = title;
                return this;
            }

            public Builder status(String status) {
                this.status = status;
                return this;
            }

            public Builder type(String type) {
                this.type = type;
                return this;
            }

            public Builder summary(String summary) {
                this.summary = summary;
                return this;
            }

            public Builder requirementNo(String requirementNo) {
                this.requirementNo = requirementNo;
                return this;
            }

            public RequirementReference build() {
                RequirementReference ref = new RequirementReference();
                ref.setId(id);
                ref.setTitle(title);
                ref.setStatus(status);
                ref.setType(type);
                ref.setSummary(summary);
                ref.setRequirementNo(requirementNo);
                return ref;
            }
        }
    }
}
