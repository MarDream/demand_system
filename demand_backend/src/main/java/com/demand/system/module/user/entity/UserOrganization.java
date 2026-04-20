package com.demand.system.module.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;

@Data
@TableName("user_organizations")
public class UserOrganization {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long regionId;

    private Long departmentId;

    private Long positionId;

    private String systemRole;

    private Long managerId;

    private LocalDate effectiveDate;
}
