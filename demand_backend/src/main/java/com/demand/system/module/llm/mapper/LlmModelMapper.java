package com.demand.system.module.llm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.demand.system.module.llm.entity.LlmModel;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LlmModelMapper extends BaseMapper<LlmModel> {
}
