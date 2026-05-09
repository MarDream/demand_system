package com.demand.system.module.auth.service;

/**
 * 验证码服务接口
 */
public interface VerificationCodeService {

    /**
     * 生成并发送验证码
     *
     * @param email 邮箱
     * @param type 类型(register/reset_password)
     */
    void generateAndSendCode(String email, String type);

    /**
     * 验证验证码
     *
     * @param email 邮箱
     * @param code 验证码
     * @param type 类型(register/reset_password)
     * @return 是否验证通过
     */
    boolean verifyCode(String email, String code, String type);

    /**
     * 标记验证码为已使用
     *
     * @param email 邮箱
     * @param code 验证码
     * @param type 类型
     */
    void markCodeAsUsed(String email, String code, String type);
}
