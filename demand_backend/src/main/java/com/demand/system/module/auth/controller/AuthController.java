package com.demand.system.module.auth.controller;

import com.demand.system.common.result.Result;
import com.demand.system.module.auth.dto.LoginRequest;
import com.demand.system.module.auth.dto.RefreshTokenRequest;
import com.demand.system.module.auth.dto.TokenResponse;
import com.demand.system.module.auth.dto.UserInfoResponse;
import com.demand.system.module.auth.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public Result<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(authService.login(request));
    }

    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        authService.logout(token);
        return Result.success();
    }

    @PostMapping("/refresh")
    public Result<TokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return Result.success(authService.refreshToken(request));
    }

    @GetMapping("/me")
    public Result<UserInfoResponse> getCurrentUser() {
        return Result.success(authService.getCurrentUser());
    }
}
