package com.demand.system.module.invitation.service;

import com.demand.system.common.result.PageResult;
import com.demand.system.module.invitation.dto.*;

/**
 * 用户邀请 / 加入申请服务
 */
public interface InvitationService {

    /** 生成一条邀请链接 */
    InvitationVO createLinkInvite(InvitationLinkCreateDTO dto);

    /** 批量邀请，逐行返回成功与被跳过的原因 */
    BatchInviteResultVO batchInvite(BatchInviteDTO dto);

    /** 邀请记录分页列表 */
    PageResult<InvitationVO> listInvitations(InvitationQueryDTO query);

    /** 撤回邀请 */
    InvitationVO revokeInvitation(Long id);

    /** 重发邀请：重置邀请码与有效期，回到待接受 */
    InvitationVO resendInvitation(Long id);

    /** 申请记录分页列表 */
    PageResult<JoinRequestVO> listJoinRequests(JoinRequestQueryDTO query);

    /** 审批通过：自动建号 / 归入组织 / 授予角色 */
    void approveJoinRequest(Long id, ReviewJoinRequestDTO dto);

    /** 审批拒绝 */
    void rejectJoinRequest(Long id, ReviewJoinRequestDTO dto);

    /** 匿名：查询邀请链接信息 */
    InviteInfoVO getInviteInfo(String code);

    /** 匿名：被邀请人提交资料，生成一条待处理申请 */
    void submitInviteApplication(String code, InviteApplyDTO dto);
}
