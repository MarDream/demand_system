package com.demand.system.module.invitation.controller;

import com.demand.system.common.result.Result;
import com.demand.system.module.invitation.dto.InviteApplyDTO;
import com.demand.system.module.invitation.dto.InviteInfoVO;
import com.demand.system.module.invitation.service.InvitationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * 邀请链接的匿名访问入口。
 * <p>
 * 路径挂在 /api/v1/public/ 下，由 SecurityConfig 统一放行，
 * 因此这里不写 @PreAuthorize —— 安全性依赖：
 * 1) 邀请码本身是高熵随机串（10 位、去易混淆字符集）；
 * 2) 所有状态校验都在 Service 的 validateUsable 里做，过期/撤回/用尽一律拒绝。
 */
@RestController
@RequestMapping("/api/v1/public/invitations")
public class PublicInvitationController {

    private final InvitationService invitationService;

    public PublicInvitationController(InvitationService invitationService) {
        this.invitationService = invitationService;
    }

    /** 查询邀请链接信息，供邀请落地页展示「XX 邀请你加入 XX 组织」 */
    @GetMapping("/{code}")
    public Result<InviteInfoVO> getInviteInfo(@PathVariable String code) {
        return Result.success(invitationService.getInviteInfo(code));
    }

    /** 被邀请人提交资料，生成一条待处理申请 */
    @PostMapping("/{code}/apply")
    public Result<Void> apply(@PathVariable String code, @Valid @RequestBody InviteApplyDTO dto) {
        invitationService.submitInviteApplication(code, dto);
        return Result.success();
    }
}
