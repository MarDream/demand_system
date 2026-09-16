package com.demand.system.module.invitation.dto;

/**
 * 邀请记录列表查询条件
 */
public class InvitationQueryDTO {

    /** link / batch，空 = 全部 */
    private String inviteType;

    /** pending / accepted / expired / revoked，空 = 全部 */
    private String status;

    /** 按被邀请人姓名/手机号/邮箱模糊匹配 */
    private String keyword;

    private Integer pageNum = 1;

    private Integer pageSize = 10;

    public String getInviteType() { return inviteType; }
    public void setInviteType(String inviteType) { this.inviteType = inviteType; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }

    public Integer getPageNum() { return pageNum; }
    public void setPageNum(Integer pageNum) { this.pageNum = pageNum; }

    public Integer getPageSize() { return pageSize; }
    public void setPageSize(Integer pageSize) { this.pageSize = pageSize; }
}
