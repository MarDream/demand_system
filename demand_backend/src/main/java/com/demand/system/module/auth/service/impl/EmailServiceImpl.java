package com.demand.system.module.auth.service.impl;

import com.demand.system.module.auth.service.EmailService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * 邮件服务实现类
 */
@Service
public class EmailServiceImpl implements EmailService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(EmailServiceImpl.class);

    private final JavaMailSender mailSender;

    public EmailServiceImpl(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Value("${spring.mail.username:noreply@demand-system.com}")
    private String from;

    @Override
    public void sendVerificationCode(String to, String code, String type) {
        try {
            log.info("准备发送验证码邮件: to={}, code={}, type={}", to, code, type);

            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);

            String subject;
            String content;

            if ("register".equals(type)) {
                subject = "【需求管理系统】注册验证码";
                content = String.format(
                    "您好！\n\n" +
                    "您正在注册需求管理系统账号，验证码为：%s\n\n" +
                    "验证码有效期为10分钟，请尽快完成注册。\n\n" +
                    "如果这不是您的操作，请忽略此邮件。\n\n" +
                    "需求管理系统",
                    code
                );
            } else if ("reset_password".equals(type)) {
                subject = "【需求管理系统】密码重置验证码";
                content = String.format(
                    "您好！\n\n" +
                    "您正在重置需求管理系统账号密码，验证码为：%s\n\n" +
                    "验证码有效期为10分钟，请尽快完成密码重置。\n\n" +
                    "如果这不是您的操作，请立即修改密码并联系管理员。\n\n" +
                    "需求管理系统",
                    code
                );
            } else {
                log.warn("未知的验证码类型: {}", type);
                return;
            }

            message.setSubject(subject);
            message.setText(content);

            mailSender.send(message);
            log.info("验证码邮件发送成功: to={}, type={}", to, type);

        } catch (Exception e) {
            log.error("发送验证码邮件失败: to={}, type={}, error={}", to, type, e.getMessage(), e);
            // 不抛出异常，避免影响接口返回
            // 在实际生产环境中，邮件服务器配置正确后，这里应该抛出异常
        }
    }

    @Override
    public void sendPasswordResetEmail(String to, String resetUrl) {
        try {
            log.info("准备发送密码重置邮件: to={}, resetUrl={}", to, resetUrl);

            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject("【需求管理系统】密码重置");
            message.setText(String.format(
                "您好！\n\n" +
                "您正在重置需求管理系统账号密码，请点击以下链接完成密码重置：\n\n" +
                "%s\n\n" +
                "链接有效期为10分钟，请尽快完成操作。\n\n" +
                "如果这不是您的操作，请立即修改密码并联系管理员。\n\n" +
                "需求管理系统",
                resetUrl
            ));

            mailSender.send(message);
            log.info("密码重置邮件发送成功: to={}", to);

        } catch (Exception e) {
            log.error("发送密码重置邮件失败: to={}, error={}", to, e.getMessage(), e);
            // 不抛出异常，避免影响接口返回
        }
    }

    @Override
    public boolean sendInitialPasswordEmail(String to, String username, String initialPassword) {
        try {
            log.info("准备发送初始密码邮件: to={}, username={}", to, username);

            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject("【需求管理系统】账号初始密码通知");
            message.setText(String.format(
                "您好！\n\n" +
                "您的需求管理系统账号已创建成功。\n\n" +
                "用户名：%s\n" +
                "初始密码：%s\n\n" +
                "请尽快登录系统并修改密码。\n\n" +
                "如果这不是您的账号，请联系管理员。\n\n" +
                "需求管理系统",
                username,
                initialPassword
            ));

            mailSender.send(message);
            log.info("初始密码邮件发送成功: to={}, username={}", to, username);
            return true;
        } catch (Exception e) {
            log.error("发送初始密码邮件失败: to={}, username={}, error={}", to, username, e.getMessage(), e);
            return false;
        }
    }
}
