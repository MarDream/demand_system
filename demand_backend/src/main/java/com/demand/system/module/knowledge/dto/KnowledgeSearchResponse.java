package com.demand.system.module.knowledge.dto;

import java.util.List;

public class KnowledgeSearchResponse {

    private List<SearchResultItem> results;
    private Integer total;
    private String answer;
    private String processSummary;

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

    public String getProcessSummary() {
        return processSummary;
    }

    public void setProcessSummary(String processSummary) {
        this.processSummary = processSummary;
    }

    public static KnowledgeSearchResponseBuilder builder() {
        return new KnowledgeSearchResponseBuilder();
    }

    public static class KnowledgeSearchResponseBuilder {
        private List<SearchResultItem> results;
        private Integer total;
        private String answer;
        private String processSummary;

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

        public KnowledgeSearchResponseBuilder processSummary(String processSummary) {
            this.processSummary = processSummary;
            return this;
        }

        public KnowledgeSearchResponse build() {
            KnowledgeSearchResponse response = new KnowledgeSearchResponse();
            response.setResults(results);
            response.setTotal(total);
            response.setAnswer(answer);
            response.setProcessSummary(processSummary);
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

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private Long id;
            private String title;
            private String status;
            private String type;
            private String summary;

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

            public RequirementReference build() {
                RequirementReference ref = new RequirementReference();
                ref.setId(id);
                ref.setTitle(title);
                ref.setStatus(status);
                ref.setType(type);
                ref.setSummary(summary);
                return ref;
            }
        }
    }
}
