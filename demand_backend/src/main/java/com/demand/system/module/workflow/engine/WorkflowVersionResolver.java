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
     * 按需求类型编码解析活跃工作流版本 —— 新旧引擎共用的唯一入口。
     * <p>解析链路：
     * <ol>
     *   <li>通过 {@code requirement_types.code} 查到 {@code workflow_version_id}</li>
     *   <li>校验该版本行存在且 {@code is_active=1 AND activation_status='active'}</li>
     *   <li>校验失败则抛 {@link WorkflowNotConfiguredException}</li>
     * </ol>
     *
     * @param typeCode 需求类型编码（如 {@code Requirement} / {@code Order} / {@code Bug} / {@code FEATURE}）
     * @return 活跃的工作流版本
     * @throws WorkflowNotConfiguredException 未绑定或绑定版本不可用时抛出
     */
    public WorkflowVersion resolveForType(String typeCode) {
        if (typeCode == null || typeCode.isBlank()) {
            throw new WorkflowNotConfiguredException("(空)", "需求类型编码不能为空");
        }

        RequirementTypeConfig typeConfig = requirementTypeMapper.selectByCode(typeCode);
        if (typeConfig == null) {
            throw new WorkflowNotConfiguredException(typeCode, "需求类型不存在");
        }
        if (typeConfig.getWorkflowVersionId() == null) {
            throw new WorkflowNotConfiguredException(typeCode);
        }

        WorkflowVersion version = workflowVersionMapper.selectById(typeConfig.getWorkflowVersionId());
        if (version == null) {
            throw new WorkflowNotConfiguredException(typeCode, "绑定的工作流版本不存在（ID=" + typeConfig.getWorkflowVersionId() + "）");
        }
        if (version.getIsActive() == null || version.getIsActive() != 1) {
            throw new WorkflowNotConfiguredException(typeCode, "工作流版本已停用");
        }
        if (!"active".equals(version.getActivationStatus())) {
            throw new WorkflowNotConfiguredException(typeCode, "工作流版本状态为 " + version.getActivationStatus() + "，需处于 active 状态");
        }

        return version;
    }

    /**
     * 静默版 resolveForType —— 不抛异常，返回 Optional。
     * <p>用于前端下拉选项过滤、getAvailableActions 等场景：未配置时返回 empty 而非异常。
     */
    public Optional<WorkflowVersion> findActiveVersionForType(String typeCode) {
        try {
            return Optional.of(resolveForType(typeCode));
        } catch (WorkflowNotConfiguredException e) {
            return Optional.empty();
        }
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
