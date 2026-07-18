package com.demand.system.module.bitable.controller;

import com.demand.system.common.result.Result;
import com.demand.system.module.auth.security.SecurityUtils;
import com.demand.system.module.bitable.dto.BitableBaseCreateDTO;
import com.demand.system.module.bitable.dto.BitableBaseMemberVO;
import com.demand.system.module.bitable.dto.BitableBaseUpdateDTO;
import com.demand.system.module.bitable.dto.BitableBaseVO;
import com.demand.system.module.bitable.service.BitableAuthorizationService;
import com.demand.system.module.bitable.service.BitableBaseMemberService;
import com.demand.system.module.bitable.service.BitableBaseService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 多维表格 Base 控制器
 */
@RestController
@RequestMapping("/api/v1/bitable")
public class BitableBaseController {

    private final BitableBaseService bitableBaseService;
    private final BitableBaseMemberService bitableBaseMemberService;
    private final BitableAuthorizationService authorizationService;

    public BitableBaseController(BitableBaseService bitableBaseService,
                                 BitableBaseMemberService bitableBaseMemberService,
                                 BitableAuthorizationService authorizationService) {
        this.bitableBaseService = bitableBaseService;
        this.bitableBaseMemberService = bitableBaseMemberService;
        this.authorizationService = authorizationService;
    }

    @GetMapping("/bases")
    @PreAuthorize("isAuthenticated()")
    public Result<List<BitableBaseVO>> listBases() {
        Long userId = SecurityUtils.getCurrentUserId();
        List<BitableBaseVO> list = bitableBaseService.listBases(userId);
        return Result.success(list);
    }

    @GetMapping("/bases/{id}")
    @PreAuthorize("isAuthenticated()")
    public Result<BitableBaseVO> getBaseById(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        authorizationService.checkReadPermission(id, userId);
        BitableBaseVO vo = bitableBaseService.getBaseById(id);
        return Result.success(vo);
    }

    @PostMapping("/bases")
    @PreAuthorize("isAuthenticated()")
    public Result<Long> createBase(@Valid @RequestBody BitableBaseCreateDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        Long id = bitableBaseService.createBase(dto, userId);
        return Result.success(id);
    }

    @PutMapping("/bases/{id}")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> updateBase(@PathVariable Long id, @RequestBody BitableBaseUpdateDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        authorizationService.checkOwnerPermission(id, userId);
        bitableBaseService.updateBase(id, dto);
        return Result.success();
    }

    @DeleteMapping("/bases/{id}")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> deleteBase(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        authorizationService.checkOwnerPermission(id, userId);
        bitableBaseService.deleteBase(id);
        // 删除后清除角色缓存
        authorizationService.clearRoleCache(id);
        return Result.success();
    }

    @GetMapping("/bases/{baseId}/members")
    @PreAuthorize("isAuthenticated()")
    public Result<List<BitableBaseMemberVO>> listMembers(@PathVariable Long baseId) {
        Long userId = SecurityUtils.getCurrentUserId();
        authorizationService.checkReadPermission(baseId, userId);
        List<BitableBaseMemberVO> list = bitableBaseMemberService.listMembers(baseId);
        return Result.success(list);
    }

    @PostMapping("/bases/{baseId}/members")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> addMember(@PathVariable Long baseId, @RequestBody Map<String, Object> body) {
        Long userId = SecurityUtils.getCurrentUserId();
        authorizationService.checkManagePermission(baseId, userId);
        Long targetUserId = parseLong(body.get("userId"));
        String role = (String) body.get("role");
        if (targetUserId == null) {
            return Result.fail("userId 不能为空");
        }
        if (role == null || role.isBlank()) {
            return Result.fail("role 不能为空");
        }
        bitableBaseMemberService.addMember(baseId, targetUserId, role);
        // 清除新成员的角色缓存
        authorizationService.clearRoleCache(baseId, targetUserId);
        return Result.success();
    }

    @PutMapping("/bases/{baseId}/members/{userId}")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> updateMemberRole(@PathVariable Long baseId,
                                         @PathVariable Long userId,
                                         @RequestBody Map<String, Object> body) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        authorizationService.checkManagePermission(baseId, currentUserId);
        String role = (String) body.get("role");
        if (role == null || role.isBlank()) {
            return Result.fail("role 不能为空");
        }
        bitableBaseMemberService.updateMemberRole(baseId, userId, role);
        // 清除被修改成员的角色缓存
        authorizationService.clearRoleCache(baseId, userId);
        return Result.success();
    }

    @DeleteMapping("/bases/{baseId}/members/{userId}")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> removeMember(@PathVariable Long baseId, @PathVariable Long userId) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        authorizationService.checkManagePermission(baseId, currentUserId);
        bitableBaseMemberService.removeMember(baseId, userId);
        // 清除被移除成员的角色缓存
        authorizationService.clearRoleCache(baseId, userId);
        return Result.success();
    }

    private Long parseLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.valueOf(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
