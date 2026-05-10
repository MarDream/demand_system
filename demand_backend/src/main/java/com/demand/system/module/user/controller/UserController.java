package com.demand.system.module.user.controller;

import com.demand.system.common.result.PageResult;
import com.demand.system.common.result.Result;
import com.demand.system.module.user.dto.UserCreateDTO;
import com.demand.system.module.user.dto.UserQueryDTO;
import com.demand.system.module.user.dto.UserUpdateDTO;
import com.demand.system.module.user.dto.UserVO;
import com.demand.system.module.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public Result<PageResult<UserVO>> list(UserQueryDTO query) {
        return Result.success(userService.list(query));
    }

    @GetMapping("/{id}")
    public Result<UserVO> getById(@PathVariable Long id) {
        return Result.success(userService.getById(id));
    }

    @PostMapping
    public Result<Void> create(@Valid @RequestBody UserCreateDTO dto) {
        userService.create(dto);
        return Result.success();
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody UserUpdateDTO dto) {
        dto.setId(id);
        userService.update(dto);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return Result.success();
    }

    @PostMapping("/{id}/send-init-password")
    public Result<String> sendInitialPassword(@PathVariable Long id) {
        boolean emailSent = userService.resetInitialPassword(id);
        if (emailSent) {
            return Result.success("初始密码已重置并发送至用户邮箱");
        }
        return Result.success("初始密码已重置，但邮件发送失败，请检查邮箱配置后重试");
    }
}
