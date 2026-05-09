package com.demand.system.module.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 验证码实体
 */
@Data
@TableName("verification_codes")
public class VerificationCode {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 邮箱地址
     */
    private String email;

    /**
     * 验证码
     */
    private String code;

    /**
     * 类型(register/reset_password)
     */
    private String type;

    /**
     * 是否已使用 0=未使用 1=已使用
     */
    private Integer used;

    /**
     * 过期时间
     */
    private LocalDateTime expiresAt;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
