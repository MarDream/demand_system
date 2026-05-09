package com.demand.system.module.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.demand.system.module.auth.entity.VerificationCode;
import org.apache.ibatis.annotations.Mapper;

/**
 * 验证码Mapper
 */
@Mapper
public interface VerificationCodeMapper extends BaseMapper<VerificationCode> {
}
