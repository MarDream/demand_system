package com.demand.system.module.bitable.dto;

import java.util.List;
import java.util.Map;

/**
 * AI 查询结果 DTO
 */
public class AiQueryResult {

    private String answer;

    private List<RecordMatch> matchedRecords;

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public List<RecordMatch> getMatchedRecords() {
        return matchedRecords;
    }

    public void setMatchedRecords(List<RecordMatch> matchedRecords) {
        this.matchedRecords = matchedRecords;
    }

    /**
     * 匹配的记录
     */
    public static class RecordMatch {

        private Long recordId;

        private String displayText; // 记录展示文本

        private Map<Long, Object> cells; // fieldId -> value

        public Long getRecordId() {
            return recordId;
        }

        public void setRecordId(Long recordId) {
            this.recordId = recordId;
        }

        public String getDisplayText() {
            return displayText;
        }

        public void setDisplayText(String displayText) {
            this.displayText = displayText;
        }

        public Map<Long, Object> getCells() {
            return cells;
        }

        public void setCells(Map<Long, Object> cells) {
            this.cells = cells;
        }
    }
}