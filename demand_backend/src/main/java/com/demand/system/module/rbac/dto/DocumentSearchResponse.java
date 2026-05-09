package com.demand.system.module.rbac.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class DocumentSearchResponse {

    private List<DocumentItem> documents;

    @Data
    @Builder
    public static class DocumentItem {
        private Long documentId;
        private String fileName;
        private Double avgScore;
        private List<ChunkItem> chunks;
    }

    @Data
    @Builder
    public static class ChunkItem {
        private String chunkId;
        private String sectionTitle;
        private String matchedText;
    }
}
