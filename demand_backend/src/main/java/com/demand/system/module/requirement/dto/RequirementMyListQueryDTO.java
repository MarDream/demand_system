package com.demand.system.module.requirement.dto;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

public class RequirementMyListQueryDTO {

    private Long projectId;

    private String type;

    private String priority;

    private String status;

    private Long assigneeId;

    private String keyword;

    /**
     * 关键词搜索范围：all(综合，标题+正文) / title / requirementNo / description / assignee / comment
     * 为空时按 all 处理
     */
    private String keywordScope;

    /** 节点状态编码 (如 IN_DEVELOPMENT) */
    private String nodeStatus;

    /** 是否逾期 */
    private Boolean isOverdue;

    /** 时间维度筛选：按所选时间字段的范围过滤（与全部需求视图共用口径） */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAtStart;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAtEnd;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime analysisCompletedAtStart;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime analysisCompletedAtEnd;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime confirmAtStart;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime confirmAtEnd;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime developmentCompletedAtStart;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime developmentCompletedAtEnd;

    private int pageNum = 1;

    private int pageSize = 10;

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getAssigneeId() {
        return assigneeId;
    }

    public void setAssigneeId(Long assigneeId) {
        this.assigneeId = assigneeId;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public String getKeywordScope() {
        return keywordScope;
    }

    public void setKeywordScope(String keywordScope) {
        this.keywordScope = keywordScope;
    }

    public String getNodeStatus() {
        return nodeStatus;
    }

    public void setNodeStatus(String nodeStatus) {
        this.nodeStatus = nodeStatus;
    }

    public Boolean getIsOverdue() {
        return isOverdue;
    }

    public void setIsOverdue(Boolean overdue) {
        isOverdue = overdue;
    }

    public LocalDateTime getCreatedAtStart() {
        return createdAtStart;
    }

    public void setCreatedAtStart(LocalDateTime createdAtStart) {
        this.createdAtStart = createdAtStart;
    }

    public LocalDateTime getCreatedAtEnd() {
        return createdAtEnd;
    }

    public void setCreatedAtEnd(LocalDateTime createdAtEnd) {
        this.createdAtEnd = createdAtEnd;
    }

    public LocalDateTime getAnalysisCompletedAtStart() {
        return analysisCompletedAtStart;
    }

    public void setAnalysisCompletedAtStart(LocalDateTime analysisCompletedAtStart) {
        this.analysisCompletedAtStart = analysisCompletedAtStart;
    }

    public LocalDateTime getAnalysisCompletedAtEnd() {
        return analysisCompletedAtEnd;
    }

    public void setAnalysisCompletedAtEnd(LocalDateTime analysisCompletedAtEnd) {
        this.analysisCompletedAtEnd = analysisCompletedAtEnd;
    }

    public LocalDateTime getConfirmAtStart() {
        return confirmAtStart;
    }

    public void setConfirmAtStart(LocalDateTime confirmAtStart) {
        this.confirmAtStart = confirmAtStart;
    }

    public LocalDateTime getConfirmAtEnd() {
        return confirmAtEnd;
    }

    public void setConfirmAtEnd(LocalDateTime confirmAtEnd) {
        this.confirmAtEnd = confirmAtEnd;
    }

    public LocalDateTime getDevelopmentCompletedAtStart() {
        return developmentCompletedAtStart;
    }

    public void setDevelopmentCompletedAtStart(LocalDateTime developmentCompletedAtStart) {
        this.developmentCompletedAtStart = developmentCompletedAtStart;
    }

    public LocalDateTime getDevelopmentCompletedAtEnd() {
        return developmentCompletedAtEnd;
    }

    public void setDevelopmentCompletedAtEnd(LocalDateTime developmentCompletedAtEnd) {
        this.developmentCompletedAtEnd = developmentCompletedAtEnd;
    }

    public int getPageNum() {
        return pageNum;
    }

    public void setPageNum(int pageNum) {
        this.pageNum = Math.max(pageNum, 1);
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = Math.min(Math.max(pageSize, 1), 100);
    }
}
