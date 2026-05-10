package com.demand.system.module.knowledge.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class KnowledgeSearchResponse {

    private List<SearchResultItem> results;

    private Integer total;

    private String answer;

    private String processSummary;

    @Data
    @Builder
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
    }

    @Data
    @Builder
    public static class RequirementReference {
        private Long id;
        private String title;
        private String status;
        private String type;
        private String summary;
    }
}
