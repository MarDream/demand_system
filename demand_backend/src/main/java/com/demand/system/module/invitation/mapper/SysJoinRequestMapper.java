package com.demand.system.module.invitation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.demand.system.module.invitation.entity.SysJoinRequest;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户加入申请记录 Mapper
 */
@Mapper
public interface SysJoinRequestMapper extends BaseMapper<SysJoinRequest> {
}
