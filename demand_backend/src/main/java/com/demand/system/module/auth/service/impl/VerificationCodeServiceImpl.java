package com.demand.system.module.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.demand.system.module.auth.entity.VerificationCode;
import com.demand.system.module.auth.mapper.VerificationCodeMapper;
import com.demand.system.module.auth.service.EmailService;
import com.demand.system.module.auth.service.VerificationCodeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Random;

/**
 * 验证码服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VerificationCodeServiceImpl implements VerificationCodeService {

    private final VerificationCodeMapper verificationCodeMapper;
    private final EmailService emailService;

    private static final int CODE_LENGTH = 6;
    private static final int CODE_EXPIRATION_MINUTES = 10;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void generateAndSendCode(String email, String type) {
        // 生成6位随机数字验证码
        String code = generateRandomCode();

        // 设置过期时间（10分钟后）
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(CODE_EXPIRATION_MINUTES);

        // 保存验证码到数据库
        VerificationCode verificationCode = new VerificationCode();
        verificationCode.setEmail(email);
        verificationCode.setCode(code);
        verificationCode.setType(type);
        verificationCode.setUsed(0);
        verificationCode.setExpiresAt(expiresAt);
        verificationCode.setCreatedAt(LocalDateTime.now());

        verificationCodeMapper.insert(verificationCode);
        log.info("验证码已保存到数据库: email={}, code={}, type={}, expiresAt={}",
                 email, code, type, expiresAt);

        // 发送验证码邮件
        emailService.sendVerificationCode(email, code, type);
    }

    @Override
    public boolean verifyCode(String email, String code, String type) {
        LambdaQueryWrapper<VerificationCode> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(VerificationCode::getEmail, email)
                    .eq(VerificationCode::getCode, code)
                    .eq(VerificationCode::getType, type)
                    .eq(VerificationCode::getUsed, 0)
                    .gt(VerificationCode::getExpiresAt, LocalDateTime.now())
                    .orderByDesc(VerificationCode::getCreatedAt)
                    .last("LIMIT 1");

        VerificationCode verificationCode = verificationCodeMapper.selectOne(queryWrapper);

        if (verificationCode == null) {
            log.warn("验证码验证失败: email={}, code={}, type={}", email, code, type);
            return false;
        }

        log.info("验证码验证成功: email={}, code={}, type={}", email, code, type);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markCodeAsUsed(String email, String code, String type) {
        LambdaUpdateWrapper<VerificationCode> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(VerificationCode::getEmail, email)
                     .eq(VerificationCode::getCode, code)
                     .eq(VerificationCode::getType, type)
                     .eq(VerificationCode::getUsed, 0)
                     .set(VerificationCode::getUsed, 1);

        int updated = verificationCodeMapper.update(null, updateWrapper);
        log.info("标记验证码为已使用: email={}, code={}, type={}, updated={}",
                 email, code, type, updated);
    }

    /**
     * 生成随机验证码
     */
    private String generateRandomCode() {
        Random random = new Random();
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(random.nextInt(10));
        }
        return code.toString();
    }
}
