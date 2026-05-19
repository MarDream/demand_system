package com.demand.system.module.auth.controller;

import com.demand.system.common.result.Result;
import com.demand.system.module.auth.dto.*;
import com.demand.system.module.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * 认证控制器
 */
@Tag(name = "认证管理", description = "用户认证相关接口")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "用户登录", description = "用户名密码登录，返回访问令牌和刷新令牌")
    @PostMapping("/login")
    public Result<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(authService.login(request));
    }

    @Operation(summary = "用户登出", description = "登出当前用户，清除令牌")
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        authService.logout(token);
        return Result.success();
    }

    @Operation(summary = "刷新令牌", description = "使用刷新令牌获取新的访问令牌")
    @PostMapping("/refresh")
    public Result<TokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return Result.success(authService.refreshToken(request));
    }

    @Operation(summary = "获取当前用户信息", description = "获取当前登录用户的详细信息")
    @GetMapping("/me")
    public Result<UserInfoResponse> getCurrentUser() {
        return Result.success(authService.getCurrentUser());
    }

    @Operation(summary = "用户注册", description = "新用户注册，需要邮箱验证码")
    @PostMapping("/register")
    public Result<TokenResponse> register(@Valid @RequestBody RegisterRequest request) {
        return Result.success(authService.register(request));
    }

    @Operation(summary = "发送验证码", description = "发送邮箱验证码，用于注册或密码重置")
    @PostMapping("/send-verification-code")
    public Result<Void> sendVerificationCode(@Valid @RequestBody SendVerificationCodeRequest request) {
        authService.sendVerificationCode(request);
        return Result.success();
    }

    @Operation(summary = "请求密码重置", description = "请求重置密码，发送验证码到邮箱")
    @PostMapping("/request-password-reset")
    public Result<Void> requestPasswordReset(@Valid @RequestBody ResetPasswordRequest request) {
        authService.requestPasswordReset(request);
        return Result.success();
    }

    @Operation(summary = "确认密码重置", description = "使用验证码重置密码")
    @PostMapping("/confirm-password-reset")
    public Result<Void> confirmPasswordReset(@Valid @RequestBody ConfirmResetPasswordRequest request) {
        authService.confirmPasswordReset(request);
        return Result.success();
    }
}
