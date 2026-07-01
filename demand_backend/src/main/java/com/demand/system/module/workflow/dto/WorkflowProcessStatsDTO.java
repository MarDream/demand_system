package com.demand.system.module.workflow.dto;

/**
 * 流程处理概览统计DTO
 */
public class WorkflowProcessStatsDTO {
    
    /**
     * 待办流程数（我的待办）
     */
    private Long pending;
    
    /**
     * 已办流程数（我参与过的非草稿需求）
     */
    private Long processed;
    
    /**
     * 我发起的流程数
     */
    private Long initiated;
    
    /**
     * 抄送我的流程数
     */
    private Long cc;

    public WorkflowProcessStatsDTO() {
    }

    public WorkflowProcessStatsDTO(Long pending, Long processed, Long initiated, Long cc) {
        this.pending = pending;
        this.processed = processed;
        this.initiated = initiated;
        this.cc = cc;
    }

    public Long getPending() {
        return pending;
    }

    public void setPending(Long pending) {
        this.pending = pending;
    }

    public Long getProcessed() {
        return processed;
    }

    public void setProcessed(Long processed) {
        this.processed = processed;
    }

    public Long getInitiated() {
        return initiated;
    }

    public void setInitiated(Long initiated) {
        this.initiated = initiated;
    }

    public Long getCc() {
        return cc;
    }

    public void setCc(Long cc) {
        this.cc = cc;
    }
}
