package com.demand.system.module.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.demand.system.module.workflow.entity.WorkflowState;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WorkflowStateMapper extends BaseMapper<WorkflowState> {
}
