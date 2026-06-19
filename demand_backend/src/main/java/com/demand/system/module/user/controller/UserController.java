package com.demand.system.module.user.controller;

import com.demand.system.common.result.PageResult;
import com.demand.system.common.result.Result;
import com.demand.system.module.user.dto.UserCreateDTO;
import com.demand.system.module.user.dto.UserQueryDTO;
import com.demand.system.module.user.dto.UserUpdateDTO;
import com.demand.system.module.user.dto.UserVO;
import com.demand.system.module.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public Result<PageResult<UserVO>> list(UserQueryDTO query) {
        return Result.success(userService.list(query));
    }

    /**
     * 获取活跃用户列表（仅 id/username/realName），供前端筛选框使用
     */
    @GetMapping("/active")
    @PreAuthorize("isAuthenticated()")
    public Result<List<Map<String, Object>>> listActiveUsers() {
        return Result.success(userService.listActiveUsers());
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public Result<UserVO> getById(@PathVariable Long id) {
        return Result.success(userService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN', 'button:user:create')")
    public Result<Void> create(@Valid @RequestBody UserCreateDTO dto) {
        userService.create(dto);
        return Result.success();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN', 'button:user:update')")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody UserUpdateDTO dto) {
        dto.setId(id);
        userService.update(dto);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN', 'button:user:delete')")
    public Result<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return Result.success();
    }

    @PostMapping("/{id}/send-init-password")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN', 'button:user:update')")
    public Result<String> sendInitialPassword(@PathVariable Long id) {
        boolean emailSent = userService.resetInitialPassword(id);
        if (emailSent) {
            return Result.success("初始密码已重置并发送至用户邮箱");
        }
        return Result.success("初始密码已重置，但邮件发送失败，请检查邮箱配置后重试");
    }

    @PutMapping("/{id}/roles")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN', 'button:user:update')")
    public Result<Void> assignRoles(@PathVariable Long id, @RequestBody List<Long> roleIds) {
        userService.assignRoles(id, roleIds);
        return Result.success();
    }

    @GetMapping("/{id}/roles")
    @PreAuthorize("isAuthenticated()")
    public Result<List<Long>> getUserRoles(@PathVariable Long id) {
        return Result.success(userService.getUserRoleIds(id));
    }
}
