package com.demand.system.module.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.demand.system.module.workflow.entity.WorkflowInstance;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface WorkflowInstanceMapper extends BaseMapper<WorkflowInstance> {

    /**
     * 按版本ID分组统计运行中的实例数（一次性查询，避免 N+1）
     *
     * @param versionIds 版本ID列表
     * @return 每条记录包含 workflowVersionId (Long) 和 cnt (Long)
     */
    List<Map<String, Object>> countRunningByVersionIds(@Param("versionIds") List<Long> versionIds);
}
