package com.demand.system.module.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.demand.system.module.workflow.entity.WorkflowInstanceTransition;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WorkflowInstanceTransitionMapper extends BaseMapper<WorkflowInstanceTransition> {
}
