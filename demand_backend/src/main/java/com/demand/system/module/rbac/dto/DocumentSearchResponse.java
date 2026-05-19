package com.demand.system.module.rbac.dto;

import java.util.List;

public class DocumentSearchResponse {

    private List<DocumentItem> documents;

    public List<DocumentItem> getDocuments() {
        return documents;
    }

    public void setDocuments(List<DocumentItem> documents) {
        this.documents = documents;
    }

    public static class DocumentItem {

        private Long documentId;
        private String fileName;
        private Double avgScore;
        private List<ChunkItem> chunks;

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

        public Double getAvgScore() {
            return avgScore;
        }

        public void setAvgScore(Double avgScore) {
            this.avgScore = avgScore;
        }

        public List<ChunkItem> getChunks() {
            return chunks;
        }

        public void setChunks(List<ChunkItem> chunks) {
            this.chunks = chunks;
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private Long documentId;
            private String fileName;
            private Double avgScore;
            private List<ChunkItem> chunks;

            public Builder documentId(Long documentId) {
                this.documentId = documentId;
                return this;
            }

            public Builder fileName(String fileName) {
                this.fileName = fileName;
                return this;
            }

            public Builder avgScore(Double avgScore) {
                this.avgScore = avgScore;
                return this;
            }

            public Builder chunks(List<ChunkItem> chunks) {
                this.chunks = chunks;
                return this;
            }

            public DocumentItem build() {
                DocumentItem item = new DocumentItem();
                item.setDocumentId(documentId);
                item.setFileName(fileName);
                item.setAvgScore(avgScore);
                item.setChunks(chunks);
                return item;
            }
        }
    }

    public static class ChunkItem {

        private String chunkId;
        private String sectionTitle;
        private String matchedText;

        public String getChunkId() {
            return chunkId;
        }

        public void setChunkId(String chunkId) {
            this.chunkId = chunkId;
        }

        public String getSectionTitle() {
            return sectionTitle;
        }

        public void setSectionTitle(String sectionTitle) {
            this.sectionTitle = sectionTitle;
        }

        public String getMatchedText() {
            return matchedText;
        }

        public void setMatchedText(String matchedText) {
            this.matchedText = matchedText;
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String chunkId;
            private String sectionTitle;
            private String matchedText;

            public Builder chunkId(String chunkId) {
                this.chunkId = chunkId;
                return this;
            }

            public Builder sectionTitle(String sectionTitle) {
                this.sectionTitle = sectionTitle;
                return this;
            }

            public Builder matchedText(String matchedText) {
                this.matchedText = matchedText;
                return this;
            }

            public ChunkItem build() {
                ChunkItem item = new ChunkItem();
                item.setChunkId(chunkId);
                item.setSectionTitle(sectionTitle);
                item.setMatchedText(matchedText);
                return item;
            }
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private List<DocumentItem> documents;

        public Builder documents(List<DocumentItem> documents) {
            this.documents = documents;
            return this;
        }

        public DocumentSearchResponse build() {
            DocumentSearchResponse response = new DocumentSearchResponse();
            response.setDocuments(documents);
            return response;
        }
    }
}
