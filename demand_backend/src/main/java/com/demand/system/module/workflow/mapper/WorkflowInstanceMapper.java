package com.demand.system.module.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.demand.system.module.workflow.entity.WorkflowInstance;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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
    @Select({
            "<script>",
            "SELECT workflow_version_id AS workflowVersionId, COUNT(*) AS cnt",
            "FROM workflow_instances",
            "WHERE workflow_version_id IN",
            "<foreach collection='versionIds' item='id' open='(' separator=',' close=')'>",
            "#{id}",
            "</foreach>",
            "AND status = 'running'",
            "GROUP BY workflow_version_id",
            "</script>"
    })
    List<Map<String, Object>> countRunningByVersionIds(@Param("versionIds") List<Long> versionIds);
}
