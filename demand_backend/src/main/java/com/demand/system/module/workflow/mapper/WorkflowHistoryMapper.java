package com.demand.system.module.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.demand.system.module.workflow.dto.WorkflowHistoryVO;
import com.demand.system.module.workflow.entity.WorkflowHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface WorkflowHistoryMapper extends BaseMapper<WorkflowHistory> {

    List<WorkflowHistoryVO> selectHistoryByVersionId(@Param("workflowVersionId") Long workflowVersionId);

    WorkflowHistoryVO selectLatestPublishByVersionId(@Param("workflowVersionId") Long workflowVersionId);
}
