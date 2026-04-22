package com.demand.system.module.requirement.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.demand.system.module.requirement.entity.RequirementTypeConfig;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RequirementTypeMapper extends BaseMapper<RequirementTypeConfig> {
}