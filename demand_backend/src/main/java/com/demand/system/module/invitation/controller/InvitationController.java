package com.demand.system.module.invitation.controller;

import com.demand.system.common.result.PageResult;
import com.demand.system.common.result.Result;
import com.demand.system.module.invitation.dto.*;
import com.demand.system.module.invitation.service.InvitationService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/invitations")
public class InvitationController {

    private final InvitationService invitationService;

    public InvitationController(InvitationService invitationService) {
        this.invitationService = invitationService;
    }

    /** 通过链接邀请：生成一条可复制的邀请链接 */
    @PostMapping("/link")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN', 'button:user:invite')")
    public Result<InvitationVO> createLinkInvite(@RequestBody InvitationLinkCreateDTO dto) {
        return Result.success(invitationService.createLinkInvite(dto));
    }

    /** 批量邀请：一次提交多个被邀请人 */
    @PostMapping("/batch")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN', 'button:user:invite')")
    public Result<BatchInviteResultVO> batchInvite(@Valid @RequestBody BatchInviteDTO dto) {
        return Result.success(invitationService.batchInvite(dto));
    }

    /** 邀请记录列表 */
    @GetMapping
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN', 'button:user:invite', 'button:user:update')")
    public Result<PageResult<InvitationVO>> list(InvitationQueryDTO query) {
        return Result.success(invitationService.listInvitations(query));
    }

    /** 撤回邀请 */
    @PutMapping("/{id}/revoke")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN', 'button:user:invite')")
    public Result<InvitationVO> revoke(@PathVariable Long id) {
        return Result.success(invitationService.revokeInvitation(id));
    }

    /** 重发邀请（换新码并重置有效期） */
    @PutMapping("/{id}/resend")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN', 'button:user:invite')")
    public Result<InvitationVO> resend(@PathVariable Long id) {
        return Result.success(invitationService.resendInvitation(id));
    }
}
