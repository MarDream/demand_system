package com.demand.system.module.auth.service;

import com.demand.system.module.auth.dto.*;

public interface AuthService {

    TokenResponse login(LoginRequest request);

    void logout(String token);

    TokenResponse refreshToken(RefreshTokenRequest request);

    UserInfoResponse getCurrentUser();

    /**
     * 个人设置：修改本人邮箱/手机号（其余信息只读，接口层面仅接受这两个字段）。
     *
     * @param request 邮箱/手机号
     * @return 更新后的当前用户信息
     */
    UserInfoResponse updateProfile(UpdateProfileRequest request);

    /**
     * 个人设置：保存本人外观配置（主题模式/主题色/圆角档位），跟随账号持久化。
     *
     * @param request 外观配置
     */
    void saveAppearanceConfig(UpdateAppearanceRequest request);

    /**
     * 个人设置：修改本人密码（需验证旧密码）。
     */
    void changePassword(ChangePasswordRequest request);

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
