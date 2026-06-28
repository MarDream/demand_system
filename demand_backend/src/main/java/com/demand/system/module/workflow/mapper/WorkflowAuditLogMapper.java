package com.demand.system.module.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.demand.system.module.workflow.entity.WorkflowAuditLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WorkflowAuditLogMapper extends BaseMapper<WorkflowAuditLog> {
}
