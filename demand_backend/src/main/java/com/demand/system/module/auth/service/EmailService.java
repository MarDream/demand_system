package com.demand.system.module.auth.service;

/**
 * 邮件服务接口
 */
public interface EmailService {

    /**
     * 发送验证码邮件
     *
     * @param to 收件人邮箱
     * @param code 验证码
     * @param type 验证码类型(register/reset_password)
     */
    void sendVerificationCode(String to, String code, String type);

    /**
     * 发送密码重置邮件
     *
     * @param to 收件人邮箱
     * @param resetUrl 重置密码链接
     */
    void sendPasswordResetEmail(String to, String resetUrl);
}
