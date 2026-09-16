package com.demand.system.module.invitation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.demand.system.module.invitation.entity.SysInvitation;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户邀请记录 Mapper
 */
@Mapper
public interface SysInvitationMapper extends BaseMapper<SysInvitation> {
}
