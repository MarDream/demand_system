package com.demand.system.module.workflow.engine;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.demand.system.module.workflow.entity.WorkflowState;
import com.demand.system.module.workflow.entity.WorkflowVersion;
import com.demand.system.module.workflow.mapper.WorkflowStateMapper;
import com.demand.system.module.workflow.mapper.WorkflowVersionMapper;
import com.demand.system.module.workflow.support.WorkflowVersionUtils;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Component
public class WorkflowVersionResolver {

    public static final Long GLOBAL_PROJECT_ID = 0L;

    private final WorkflowVersionMapper workflowVersionMapper;
    private final WorkflowStateMapper workflowStateMapper;

    public WorkflowVersionResolver(WorkflowVersionMapper workflowVersionMapper, WorkflowStateMapper workflowStateMapper) {
        this.workflowVersionMapper = workflowVersionMapper;
        this.workflowStateMapper = workflowStateMapper;
    }

    public Optional<WorkflowVersion> findActiveVersion(Long projectId) {
        List<WorkflowVersion> directVersions = workflowVersionMapper.selectList(new LambdaQueryWrapper<WorkflowVersion>()
                .eq(WorkflowVersion::getProjectId, projectId)
                .eq(WorkflowVersion::getIsActive, 1));
        WorkflowVersion direct = directVersions.stream()
                .sorted(WorkflowVersionUtils.byVersionDesc())
                .findFirst()
                .orElse(null);
        if (direct != null || projectId == null || Objects.equals(projectId, GLOBAL_PROJECT_ID)) {
            return Optional.ofNullable(direct);
        }
        List<WorkflowVersion> globalVersions = workflowVersionMapper.selectList(new LambdaQueryWrapper<WorkflowVersion>()
                .eq(WorkflowVersion::getProjectId, GLOBAL_PROJECT_ID)
                .eq(WorkflowVersion::getIsActive, 1));
        WorkflowVersion global = globalVersions.stream()
                .sorted(WorkflowVersionUtils.byVersionDesc())
                .findFirst()
                .orElse(null);
        return Optional.ofNullable(global);
    }

    public Long resolveRuntimeProjectId(Long projectId) {
        if (projectId == null || Objects.equals(projectId, GLOBAL_PROJECT_ID)) {
            return projectId;
        }
        long projectStateCount = workflowStateMapper.selectCount(new LambdaQueryWrapper<WorkflowState>()
                .eq(WorkflowState::getProjectId, projectId));
        if (projectStateCount > 0) {
            return projectId;
        }
        long globalStateCount = workflowStateMapper.selectCount(new LambdaQueryWrapper<WorkflowState>()
                .eq(WorkflowState::getProjectId, GLOBAL_PROJECT_ID));
        return globalStateCount > 0 ? GLOBAL_PROJECT_ID : projectId;
    }
}
