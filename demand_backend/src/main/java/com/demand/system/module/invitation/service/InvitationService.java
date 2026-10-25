package com.demand.system.module.invitation.service;

import com.demand.system.common.result.PageResult;
import com.demand.system.module.invitation.dto.*;

public interface InvitationService {

    InvitationVO createLinkInvite(InvitationLinkCreateDTO dto);

    /** 批量邀请，逐行返回成功与被跳过的原因 */
    BatchInviteResultVO batchInvite(BatchInviteDTO dto);

    PageResult<InvitationVO> listInvitations(InvitationQueryDTO query);

    InvitationVO revokeInvitation(Long id);

    /** 重发邀请：重置邀请码与有效期，回到待接受 */
    InvitationVO resendInvitation(Long id);

    PageResult<JoinRequestVO> listJoinRequests(JoinRequestQueryDTO query);

    /** 审批通过：自动建号 / 归入组织 / 授予角色 */
    void approveJoinRequest(Long id, ReviewJoinRequestDTO dto);

    void rejectJoinRequest(Long id, ReviewJoinRequestDTO dto);

    /** 匿名：查询邀请链接信息 */
    InviteInfoVO getInviteInfo(String code);

    /** 匿名：被邀请人提交资料，生成一条待处理申请 */
    void submitInviteApplication(String code, InviteApplyDTO dto);
}
