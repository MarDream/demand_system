package com.demand.system.module.invitation.controller;

import com.demand.system.common.result.PageResult;
import com.demand.system.common.result.Result;
import com.demand.system.module.invitation.dto.JoinRequestQueryDTO;
import com.demand.system.module.invitation.dto.JoinRequestVO;
import com.demand.system.module.invitation.dto.ReviewJoinRequestDTO;
import com.demand.system.module.invitation.service.InvitationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/join-requests")
public class JoinRequestController {

    private final InvitationService invitationService;

    public JoinRequestController(InvitationService invitationService) {
        this.invitationService = invitationService;
    }

    /** 添加/申请记录列表 */
    @GetMapping
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN', 'button:user:update', 'button:user:create')")
    public Result<PageResult<JoinRequestVO>> list(JoinRequestQueryDTO query) {
        return Result.success(invitationService.listJoinRequests(query));
    }

    /** 审批通过：自动建号、归入组织、授予角色 */
    @PutMapping("/{id}/approve")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN', 'button:user:create')")
    public Result<Void> approve(@PathVariable Long id, @RequestBody(required = false) ReviewJoinRequestDTO dto) {
        invitationService.approveJoinRequest(id, dto);
        return Result.success();
    }

    /** 审批拒绝 */
    @PutMapping("/{id}/reject")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN', 'button:user:update')")
    public Result<Void> reject(@PathVariable Long id, @RequestBody(required = false) ReviewJoinRequestDTO dto) {
        invitationService.rejectJoinRequest(id, dto);
        return Result.success();
    }
}
