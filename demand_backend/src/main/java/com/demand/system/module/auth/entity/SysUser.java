package com.demand.system.module.auth.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("users")
public class SysUser {

    private Long id;

    private String username;

    private String password;

    private String realName;

    private String email;

    private String phone;

    private String avatar;

    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deletedAt;
}
