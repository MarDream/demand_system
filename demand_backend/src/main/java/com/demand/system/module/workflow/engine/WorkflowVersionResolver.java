package com.demand.system.module.workflow.engine;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.demand.system.common.exception.WorkflowNotConfiguredException;
import com.demand.system.module.requirement.entity.RequirementTypeConfig;
import com.demand.system.module.requirement.mapper.RequirementTypeMapper;
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
    private final RequirementTypeMapper requirementTypeMapper;

    public WorkflowVersionResolver(WorkflowVersionMapper workflowVersionMapper,
                                   WorkflowStateMapper workflowStateMapper,
                                   RequirementTypeMapper requirementTypeMapper) {
        this.workflowVersionMapper = workflowVersionMapper;
        this.workflowStateMapper = workflowStateMapper;
        this.requirementTypeMapper = requirementTypeMapper;
    }

    /**
     * 按需求类型解析活跃工作流版本（不抛异常，返回 Optional）。
     * <p>用于前端下拉选项过滤、getAvailableActions 等场景：未配置时返回 empty 而非异常。
     * <p>避免 try-catch 异常控制流开销——直接做 null/状态判断。
     */
    public Optional<WorkflowVersion> findActiveVersionForType(String typeCode) {
        if (typeCode == null || typeCode.isBlank()) {
            return Optional.empty();
        }
        RequirementTypeConfig typeConfig = requirementTypeMapper.selectByCode(typeCode);
        if (typeConfig == null || typeConfig.getWorkflowVersionId() == null) {
            return Optional.empty();
        }
        WorkflowVersion version = workflowVersionMapper.selectById(typeConfig.getWorkflowVersionId());
        if (version == null || !Boolean.TRUE.equals(version.getIsActive()) || !"active".equals(version.getActivationStatus())) {
            return Optional.empty();
        }
        return Optional.of(version);
    }

    /**
     * 按需求类型解析活跃工作流版本（抛异常版，用于必须存在工作流的业务场景）。
     * 内部委托 {@link #findActiveVersionForType}，仅在结果为空时抛异常。
     */
    public WorkflowVersion resolveForType(String typeCode) {
        return findActiveVersionForType(typeCode)
                .orElseThrow(() -> new WorkflowNotConfiguredException(typeCode));
    }

    /**
     * @deprecated 保留兼容旧代码，新代码请使用 {@link #resolveForType(String)} 或 {@link #findActiveVersionForType(String)}
     */
    @Deprecated
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
