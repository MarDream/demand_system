package com.demand.system.module.auth.service;

import com.demand.system.module.auth.dto.*;

public interface AuthService {

    TokenResponse login(LoginRequest request);

    void logout(String token);

    TokenResponse refreshToken(RefreshTokenRequest request);

    UserInfoResponse getCurrentUser();

    /**
     * 用户注册
     *
     * @param request 注册请求
     * @return Token响应
     */
    TokenResponse register(RegisterRequest request);

    /**
     * 发送验证码
     *
     * @param request 发送验证码请求
     */
    void sendVerificationCode(SendVerificationCodeRequest request);

    /**
     * 请求密码重置（发送验证码）
     *
     * @param request 密码重置请求
     */
    void requestPasswordReset(ResetPasswordRequest request);

    /**
     * 确认密码重置（验证码+新密码）
     *
     * @param request 确认密码重置请求
     */
    void confirmPasswordReset(ConfirmResetPasswordRequest request);

    /**
     * 用户自助绑定组织（无组织用户首次登录强制使用）
     *
     * @param orgId 组织节点ID
     */
    void bindOrg(Long orgId);
}
