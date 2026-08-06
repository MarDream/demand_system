package com.demand.system.module.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.demand.system.module.auth.mapper.SysUserMapper;
import com.demand.system.module.auth.entity.SysUser;
import com.demand.system.module.project.entity.Project;
import com.demand.system.module.project.mapper.ProjectMapper;
import com.demand.system.module.workflow.dto.WorkflowDefinitionInfoDTO;
import com.demand.system.module.workflow.entity.WorkflowDefinition;
import com.demand.system.module.workflow.entity.WorkflowVersion;
import com.demand.system.module.workflow.engine.WorkflowVersionResolver;
import com.demand.system.module.workflow.mapper.WorkflowDefinitionMapper;
import com.demand.system.module.workflow.mapper.WorkflowVersionMapper;
import com.demand.system.module.workflow.service.WorkflowDefinitionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class WorkflowDefinitionServiceImpl implements WorkflowDefinitionService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowDefinitionServiceImpl.class);
    private static final String GLOBAL_WORKFLOW_NAME = "全局流程";

    private final WorkflowDefinitionMapper workflowDefinitionMapper;
    private final WorkflowVersionMapper workflowVersionMapper;
    private final ProjectMapper projectMapper;
    private final SysUserMapper sysUserMapper;

    public WorkflowDefinitionServiceImpl(WorkflowDefinitionMapper workflowDefinitionMapper,
                                          WorkflowVersionMapper workflowVersionMapper,
                                          ProjectMapper projectMapper,
                                          SysUserMapper sysUserMapper) {
        this.workflowDefinitionMapper = workflowDefinitionMapper;
        this.workflowVersionMapper = workflowVersionMapper;
        this.projectMapper = projectMapper;
        this.sysUserMapper = sysUserMapper;
    }

    @Override
    public List<WorkflowDefinitionInfoDTO> listAll() {
        List<WorkflowDefinition> definitions = workflowDefinitionMapper.selectList(
                new LambdaQueryWrapper<WorkflowDefinition>()
                        .orderByDesc(WorkflowDefinition::getCreatedAt));

        if (definitions.isEmpty()) {
            return new ArrayList<>();
        }

        // 聚合版本数与启用版本数
        List<WorkflowVersion> allVersions = workflowVersionMapper.selectList(new LambdaQueryWrapper<>());
        Map<Long, List<WorkflowVersion>> versionsByDefinition = allVersions.stream()
                .filter(v -> v.getWorkflowDefinitionId() != null)
                .collect(Collectors.groupingBy(WorkflowVersion::getWorkflowDefinitionId));

        return definitions.stream()
                .map(d -> toInfoDTO(d, versionsByDefinition.getOrDefault(d.getId(), List.of())))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int backfill() {
        // 扫描所有未关联定义的版本
        List<WorkflowVersion> orphanVersions = workflowVersionMapper.selectList(
                new LambdaQueryWrapper<WorkflowVersion>()
                        .isNull(WorkflowVersion::getWorkflowDefinitionId));

        if (orphanVersions.isEmpty()) {
            return 0;
        }

        // 按 (projectId, name) 分组——每组对应一个工作流定义
        Map<String, List<WorkflowVersion>> groups = new LinkedHashMap<>();
        for (WorkflowVersion v : orphanVersions) {
            Long projectId = normalizeProjectId(v.getProjectId());
            String name = StringUtils.hasText(v.getName()) ? v.getName().trim() : "未命名工作流";
            String key = projectId + "::" + name;
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(v);
        }

        int updated = 0;
        for (Map.Entry<String, List<WorkflowVersion>> entry : groups.entrySet()) {
            List<WorkflowVersion> groupVersions = entry.getValue();
            WorkflowVersion first = groupVersions.get(0);
            Long projectId = normalizeProjectId(first.getProjectId());
            String name = StringUtils.hasText(first.getName()) ? first.getName().trim() : "未命名工作流";

            // 查现有定义（按 projectId + name）
            WorkflowDefinition existing = workflowDefinitionMapper.selectOne(
                    new LambdaQueryWrapper<WorkflowDefinition>()
                            .eq(WorkflowDefinition::getProjectId, projectId)
                            .eq(WorkflowDefinition::getName, name)
                            .last("LIMIT 1"));

            Long definitionId;
            if (existing != null) {
                definitionId = existing.getId();
            } else {
                WorkflowDefinition def = new WorkflowDefinition();
                def.setName(name);
                def.setProjectId(projectId);
                def.setCreatorId(first.getCreatorId());
                workflowDefinitionMapper.insert(def);
                definitionId = def.getId();
            }

            // 回填该组所有版本
            for (WorkflowVersion v : groupVersions) {
                v.setWorkflowDefinitionId(definitionId);
                workflowVersionMapper.updateById(v);
                updated++;
            }
        }

        log.info("Workflow definition backfill finished, created definitions={}, updated versions={}", groups.size(), updated);
        return updated;
    }

    private WorkflowDefinitionInfoDTO toInfoDTO(WorkflowDefinition def, List<WorkflowVersion> versions) {
        WorkflowDefinitionInfoDTO dto = new WorkflowDefinitionInfoDTO();
        dto.setId(def.getId());
        dto.setName(def.getName());
        dto.setProjectId(def.getProjectId());
        dto.setProjectName(resolveProjectName(def.getProjectId()));
        dto.setDescription(def.getDescription());
        dto.setCreatorId(def.getCreatorId());
        dto.setCreatorName(resolveCreatorName(def.getCreatorId()));
        dto.setCreatedAt(def.getCreatedAt());
        dto.setVersionCount(versions.size());
        dto.setActiveVersionCount((int) versions.stream()
                .filter(v -> Objects.equals(v.getIsActive(), 1))
                .count());
        return dto;
    }

    private String resolveProjectName(Long projectId) {
        if (WorkflowVersionResolver.GLOBAL_PROJECT_ID.equals(projectId)) {
            return GLOBAL_WORKFLOW_NAME;
        }
        Project project = projectMapper.selectById(projectId);
        return project != null ? project.getName() : "项目 " + projectId;
    }

    private String resolveCreatorName(Long creatorId) {
        if (creatorId == null) {
            return null;
        }
        SysUser user = sysUserMapper.selectById(creatorId);
        if (user == null) {
            return null;
        }
        return StringUtils.hasText(user.getRealName()) ? user.getRealName() : user.getUsername();
    }

    private Long normalizeProjectId(Long projectId) {
        if (projectId == null || projectId <= 0) {
            return WorkflowVersionResolver.GLOBAL_PROJECT_ID;
        }
        return projectId;
    }
}
