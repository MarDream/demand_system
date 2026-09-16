package com.demand.system.module.invitation.dto;

/**
 * 申请记录列表查询条件
 */
public class JoinRequestQueryDTO {

    /** pending / approved / rejected，空 = 全部 */
    private String status;

    /** link / batch / admin / self，空 = 全部 */
    private String source;

    /** 按申请人姓名/手机号/邮箱模糊匹配 */
    private String keyword;

    private Integer pageNum = 1;

    private Integer pageSize = 10;

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }

    public Integer getPageNum() { return pageNum; }
    public void setPageNum(Integer pageNum) { this.pageNum = pageNum; }

    public Integer getPageSize() { return pageSize; }
    public void setPageSize(Integer pageSize) { this.pageSize = pageSize; }
}
