package com.demand.system.module.git.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.demand.system.module.git.entity.BranchProtectionRule;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BranchProtectionRuleMapper extends BaseMapper<BranchProtectionRule> {
}
