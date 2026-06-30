package com.demand.system.module.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.demand.system.common.exception.BusinessException;
import com.demand.system.common.result.ErrorCode;
import com.demand.system.module.auth.security.SecurityUtils;
import com.demand.system.module.auth.mapper.SysUserMapper;
import com.demand.system.module.auth.entity.SysUser;
import com.demand.system.module.requirement.entity.Requirement;
import com.demand.system.module.requirement.mapper.RequirementMapper;
import com.demand.system.module.workflow.dto.CreateMigrationPlanRequest;
import com.demand.system.module.workflow.dto.MigrationPlanVO;
import com.demand.system.module.workflow.dto.MigrationPreviewVO;
import com.demand.system.module.workflow.dto.MigrationResultDTO;
import com.demand.system.module.workflow.engine.WorkflowRuntimeLoader;
import com.demand.system.module.workflow.engine.WorkflowGraphContext;
import com.demand.system.module.workflow.entity.*;
import com.demand.system.module.workflow.mapper.WorkflowInstanceMapper;
import com.demand.system.module.workflow.mapper.WorkflowMigrationLogMapper;
import com.demand.system.module.workflow.mapper.WorkflowMigrationPlanMapper;
import com.demand.system.module.workflow.mapper.WorkflowVersionMapper;
import com.demand.system.module.workflow.support.WorkflowNodeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 工作流版本迁移服务
 *
 * ADR-002 重构（2026-06-30）：
 * - 增加 MigrationPlan CRUD — 创建/更新/预检/执行
 * - 显式节点映射 — 管理员手动配置旧节点→新节点
 * - 自动建议映射 — 按 nodeStatusCode / nodeName 自动匹配
 * - 逐条独立事务执行 — 失败不阻断
 * - 完整审计日志 — 记录映射关系
 */
@Service
public class WorkflowMigrationService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowMigrationService.class);

    private final WorkflowInstanceMapper workflowInstanceMapper;
    private final WorkflowVersionMapper workflowVersionMapper;
    private final WorkflowMigrationLogMapper workflowMigrationLogMapper;
    private final WorkflowMigrationPlanMapper workflowMigrationPlanMapper;
    private final RequirementMapper requirementMapper;
    private final WorkflowRuntimeLoader workflowRuntimeLoader;
    private final SysUserMapper sysUserMapper;

    public WorkflowMigrationService(WorkflowInstanceMapper workflowInstanceMapper,
                                    WorkflowVersionMapper workflowVersionMapper,
                                    WorkflowMigrationLogMapper workflowMigrationLogMapper,
                                    WorkflowMigrationPlanMapper workflowMigrationPlanMapper,
                                    RequirementMapper requirementMapper,
                                    WorkflowRuntimeLoader workflowRuntimeLoader,
                                    SysUserMapper sysUserMapper) {
        this.workflowInstanceMapper = workflowInstanceMapper;
        this.workflowVersionMapper = workflowVersionMapper;
        this.workflowMigrationLogMapper = workflowMigrationLogMapper;
        this.workflowMigrationPlanMapper = workflowMigrationPlanMapper;
        this.requirementMapper = requirementMapper;
        this.workflowRuntimeLoader = workflowRuntimeLoader;
        this.sysUserMapper = sysUserMapper;
    }

    // ==================== 迁移计划 CRUD ====================

    /**
     * 创建迁移计划（草稿状态），自动建议节点映射
     */
    @Transactional(rollbackFor = Exception.class)
    public MigrationPlanVO createMigrationPlan(CreateMigrationPlanRequest request) {
        Long operatorId = SecurityUtils.getCurrentUserId();

        WorkflowVersion fromVersion = workflowVersionMapper.selectById(request.getFromVersionId());
        WorkflowVersion toVersion = workflowVersionMapper.selectById(request.getToVersionId());

        if (fromVersion == null || toVersion == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "工作流版本不存在");
        }
        if (!fromVersion.getProjectId().equals(toVersion.getProjectId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "源版本与目标版本必须属于同一项目");
        }
        if (toVersion.getIsActive() != 1) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "目标版本必须是已启用状态");
        }

        // 加载两个版本的节点
        List<WorkflowNode> fromNodes = workflowRuntimeLoader.loadNodes(fromVersion.getId());
        List<WorkflowNode> toNodes = workflowRuntimeLoader.loadNodes(toVersion.getId());

        // 自动建议映射
        List<MigrationPlanVO.NodeMappingVO> mapping = buildAutoSuggestedMapping(fromNodes, toNodes);

        // 统计待迁移实例数
        Long runningCount = workflowInstanceMapper.selectCount(new LambdaQueryWrapper<WorkflowInstance>()
                .eq(WorkflowInstance::getWorkflowVersionId, fromVersion.getId())
                .eq(WorkflowInstance::getStatus, "running"));

        // 保存计划
        WorkflowMigrationPlan plan = new WorkflowMigrationPlan();
        plan.setFromVersionId(fromVersion.getId());
        plan.setToVersionId(toVersion.getId());
        plan.setProjectId(fromVersion.getProjectId());
        plan.setNodeMapping(convertToEntityMapping(mapping));
        plan.setStatus("draft");
        plan.setTotalInstanceCount(runningCount.intValue());
        plan.setMigratedCount(0);
        plan.setFailedCount(0);
        plan.setOperatorId(operatorId);
        plan.setRemark(request.getRemark());
        workflowMigrationPlanMapper.insert(plan);

        return toPlanVO(plan, fromVersion, toVersion, mapping);
    }

    /**
     * 更新节点映射配置
     */
    @Transactional(rollbackFor = Exception.class)
    public MigrationPlanVO updateNodeMapping(Long planId, List<WorkflowMigrationPlan.NodeMappingItem> nodeMapping) {
        WorkflowMigrationPlan plan = workflowMigrationPlanMapper.selectById(planId);
        if (plan == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "迁移计划不存在");
        }
        if (!"draft".equals(plan.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "只有草稿状态的计划才能修改映射");
        }

        // 校验目标节点在新版本中存在
        WorkflowGraphContext toContext = workflowRuntimeLoader.loadContext(plan.getToVersionId());
        for (WorkflowMigrationPlan.NodeMappingItem item : nodeMapping) {
            if (StringUtils.hasText(item.getToNodeId()) && toContext.getNode(item.getToNodeId()) == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST,
                        "目标节点不存在: " + item.getToNodeId());
            }
        }

        plan.setNodeMapping(nodeMapping);
        workflowMigrationPlanMapper.updateById(plan);

        WorkflowVersion fromVersion = workflowVersionMapper.selectById(plan.getFromVersionId());
        WorkflowVersion toVersion = workflowVersionMapper.selectById(plan.getToVersionId());
        return toPlanVO(plan, fromVersion, toVersion, convertToVOMapping(nodeMapping));
    }

    /**
     * 预检迁移计划
     */
    public MigrationPreviewVO previewMigration(Long planId) {
        WorkflowMigrationPlan plan = workflowMigrationPlanMapper.selectById(planId);
        if (plan == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "迁移计划不存在");
        }

        // 构建映射查找表
        Map<String, WorkflowMigrationPlan.NodeMappingItem> mappingMap = plan.getNodeMapping().stream()
                .collect(Collectors.toMap(WorkflowMigrationPlan.NodeMappingItem::getFromNodeId, m -> m, (a, b) -> a));

        // 查找所有运行中实例
        List<WorkflowInstance> instances = workflowInstanceMapper.selectList(new LambdaQueryWrapper<WorkflowInstance>()
                .eq(WorkflowInstance::getWorkflowVersionId, plan.getFromVersionId())
                .eq(WorkflowInstance::getStatus, "running"));

        // 加载旧版本节点名称
        WorkflowGraphContext fromContext = workflowRuntimeLoader.loadContext(plan.getFromVersionId());

        MigrationPreviewVO preview = new MigrationPreviewVO();
        int canMigrate = 0;
        int needManual = 0;
        List<MigrationPreviewVO.InstancePreviewItem> items = new ArrayList<>();

        for (WorkflowInstance instance : instances) {
            MigrationPreviewVO.InstancePreviewItem item = new MigrationPreviewVO.InstancePreviewItem();
            item.setInstanceId(instance.getId());
            item.setRequirementId(instance.getRequirementId());
            item.setCurrentNodeId(instance.getCurrentNodeId());

            WorkflowNode currentNode = fromContext.getNode(instance.getCurrentNodeId());
            item.setCurrentNodeName(currentNode != null ? currentNode.getNodeName() : instance.getCurrentNodeId());

            WorkflowMigrationPlan.NodeMappingItem mapping = mappingMap.get(instance.getCurrentNodeId());
            if (mapping != null && StringUtils.hasText(mapping.getToNodeId())) {
                item.setMapped(true);
                item.setMappedToNodeId(mapping.getToNodeId());
                item.setMappedToNodeName(mapping.getToNodeName());
                canMigrate++;
            } else {
                item.setMapped(false);
                needManual++;
            }
            items.add(item);
        }

        preview.setTotalInstances(instances.size());
        preview.setCanMigrateCount(canMigrate);
        preview.setNeedManualCount(needManual);
        preview.setItems(items);
        return preview;
    }

    /**
     * 执行迁移计划
     * 逐条实例执行，每条独立事务，失败不阻断
     */
    public MigrationResultDTO executeMigration(Long planId) {
        WorkflowMigrationPlan plan = workflowMigrationPlanMapper.selectById(planId);
        if (plan == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "迁移计划不存在");
        }
        if (!"draft".equals(plan.getStatus()) && !"pending".equals(plan.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "只有草稿或待执行状态的计划才能执行");
        }

        // 更新状态为执行中
        plan.setStatus("executing");
        plan.setStartedAt(LocalDateTime.now());
        workflowMigrationPlanMapper.updateById(plan);

        // 构建映射查找表
        Map<String, WorkflowMigrationPlan.NodeMappingItem> mappingMap = plan.getNodeMapping().stream()
                .filter(m -> StringUtils.hasText(m.getToNodeId()))
                .collect(Collectors.toMap(WorkflowMigrationPlan.NodeMappingItem::getFromNodeId, m -> m, (a, b) -> a));

        // 查找所有运行中实例
        List<WorkflowInstance> instances = workflowInstanceMapper.selectList(new LambdaQueryWrapper<WorkflowInstance>()
                .eq(WorkflowInstance::getWorkflowVersionId, plan.getFromVersionId())
                .eq(WorkflowInstance::getStatus, "running"));

        int successCount = 0;
        int failedCount = 0;
        List<String> warnings = new ArrayList<>();

        for (WorkflowInstance instance : instances) {
            try {
                boolean ok = migrateOneInstance(instance, plan, mappingMap);
                if (ok) {
                    successCount++;
                } else {
                    failedCount++;
                    warnings.add(String.format("需求#%d: 当前节点未在映射表中，跳过", instance.getRequirementId()));
                }
            } catch (Exception e) {
                failedCount++;
                warnings.add(String.format("需求#%d: %s", instance.getRequirementId(), e.getMessage()));
                log.warn("迁移实例失败: instanceId={}, requirementId={}", instance.getId(), instance.getRequirementId(), e);
            }
        }

        // 更新计划状态
        plan.setStatus(failedCount == 0 ? "completed" : (successCount > 0 ? "completed" : "failed"));
        plan.setMigratedCount(successCount);
        plan.setFailedCount(failedCount);
        plan.setCompletedAt(LocalDateTime.now());
        workflowMigrationPlanMapper.updateById(plan);

        String message = String.format("迁移完成：成功 %d，失败 %d，跳过 %d",
                successCount, failedCount, instances.size() - successCount - failedCount);

        return new MigrationResultDTO(instances.size(), successCount, failedCount, message, planId, warnings);
    }

    /**
     * 单条实例迁移（独立事务）
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean migrateOneInstance(WorkflowInstance instance, WorkflowMigrationPlan plan,
                                       Map<String, WorkflowMigrationPlan.NodeMappingItem> mappingMap) {
        // 1. 查找当前节点映射
        WorkflowMigrationPlan.NodeMappingItem currentMapping = mappingMap.get(instance.getCurrentNodeId());
        if (currentMapping == null) {
            return false;
        }

        String mappedCurrentNodeId = currentMapping.getToNodeId();

        // 2. 查找 previousNodeId 映射（可选）
        String mappedPreviousNodeId = null;
        if (StringUtils.hasText(instance.getPreviousNodeId())) {
            WorkflowMigrationPlan.NodeMappingItem prevMapping = mappingMap.get(instance.getPreviousNodeId());
            if (prevMapping != null) {
                mappedPreviousNodeId = prevMapping.getToNodeId();
            }
        }

        // 3. 更新工作流实例
        int updated = workflowInstanceMapper.update(null, new LambdaUpdateWrapper<WorkflowInstance>()
                .eq(WorkflowInstance::getId, instance.getId())
                .eq(WorkflowInstance::getWorkflowVersionId, plan.getFromVersionId()) // 乐观锁
                .eq(WorkflowInstance::getLockVersion, instance.getLockVersion())     // 版本锁
                .set(WorkflowInstance::getWorkflowVersionId, plan.getToVersionId())
                .set(WorkflowInstance::getCurrentNodeId, mappedCurrentNodeId)
                .set(WorkflowInstance::getPreviousNodeId, mappedPreviousNodeId)
                .set(WorkflowInstance::getLockVersion, instance.getLockVersion() + 1));

        if (updated <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "工作流实例已被修改，请刷新后重试");
        }

        // 4. 更新需求状态
        WorkflowGraphContext toContext = workflowRuntimeLoader.loadContext(plan.getToVersionId());
        WorkflowNode targetNode = toContext.getNode(mappedCurrentNodeId);
        if (targetNode != null) {
            String nodeStatusCode = WorkflowNodeUtils.resolveNodeStatusCode(targetNode, false);
            LambdaUpdateWrapper<Requirement> reqUpdate = new LambdaUpdateWrapper<Requirement>()
                    .eq(Requirement::getId, instance.getRequirementId())
                    .set(Requirement::getStatus, targetNode.getNodeName());
            if (StringUtils.hasText(nodeStatusCode)) {
                reqUpdate.set(Requirement::getNodeStatus, nodeStatusCode);
            }
            requirementMapper.update(null, reqUpdate);
        }

        // 5. 记录迁移日志
        WorkflowMigrationLog migrationLog = new WorkflowMigrationLog();
        migrationLog.setFromVersionId(plan.getFromVersionId());
        migrationLog.setToVersionId(plan.getToVersionId());
        migrationLog.setFromNodeId(instance.getCurrentNodeId());
        migrationLog.setToNodeId(mappedCurrentNodeId);
        migrationLog.setFromNodeName(currentMapping.getFromNodeName());
        migrationLog.setToNodeName(currentMapping.getToNodeName());
        migrationLog.setNodeMappingJson(plan.getNodeMapping());
        migrationLog.setRequirementId(instance.getRequirementId());
        migrationLog.setInstanceId(instance.getId());
        migrationLog.setPlanId(plan.getId());
        migrationLog.setMigrationType("plan");
        migrationLog.setMigrationStatus("success");
        migrationLog.setOperatorId(plan.getOperatorId());
        workflowMigrationLogMapper.insert(migrationLog);

        return true;
    }

    // ==================== 查询 ====================

    /**
     * 查询迁移计划列表
     */
    public List<MigrationPlanVO> listMigrationPlans(Long projectId) {
        LambdaQueryWrapper<WorkflowMigrationPlan> wrapper = new LambdaQueryWrapper<>();
        if (projectId != null) {
            wrapper.eq(WorkflowMigrationPlan::getProjectId, projectId);
        }
        wrapper.orderByDesc(WorkflowMigrationPlan::getCreatedAt);

        List<WorkflowMigrationPlan> plans = workflowMigrationPlanMapper.selectList(wrapper);
        List<MigrationPlanVO> result = new ArrayList<>();
        for (WorkflowMigrationPlan plan : plans) {
            WorkflowVersion fromVersion = workflowVersionMapper.selectById(plan.getFromVersionId());
            WorkflowVersion toVersion = workflowVersionMapper.selectById(plan.getToVersionId());
            result.add(toPlanVO(plan, fromVersion, toVersion, convertToVOMapping(plan.getNodeMapping())));
        }
        return result;
    }

    /**
     * 查询迁移计划详情
     */
    public MigrationPlanVO getMigrationPlan(Long planId) {
        WorkflowMigrationPlan plan = workflowMigrationPlanMapper.selectById(planId);
        if (plan == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "迁移计划不存在");
        }
        WorkflowVersion fromVersion = workflowVersionMapper.selectById(plan.getFromVersionId());
        WorkflowVersion toVersion = workflowVersionMapper.selectById(plan.getToVersionId());
        return toPlanVO(plan, fromVersion, toVersion, convertToVOMapping(plan.getNodeMapping()));
    }

    /**
     * 查询迁移日志
     */
    public List<WorkflowMigrationLog> listMigrationLogs(Long planId) {
        return workflowMigrationLogMapper.selectList(new LambdaQueryWrapper<WorkflowMigrationLog>()
                .eq(planId != null, WorkflowMigrationLog::getPlanId, planId)
                .orderByDesc(WorkflowMigrationLog::getCreatedAt));
    }

    /**
     * 查询版本关联的运行中工单数量
     */
    public Long countRunningInstances(Long versionId) {
        return workflowInstanceMapper.selectCount(
                new LambdaQueryWrapper<WorkflowInstance>()
                        .eq(WorkflowInstance::getWorkflowVersionId, versionId)
                        .eq(WorkflowInstance::getStatus, "running")
        );
    }

    // ==================== 私有方法 ====================

    /**
     * 自动建议节点映射
     * 策略：先按 nodeStatusCode 匹配 → 再按 nodeName 匹配 → 未匹配标记为 null
     */
    private List<MigrationPlanVO.NodeMappingVO> buildAutoSuggestedMapping(
            List<WorkflowNode> fromNodes, List<WorkflowNode> toNodes) {

        // 只映射等待节点（审批/结束等非开始节点）
        List<WorkflowNode> fromWaitNodes = fromNodes.stream()
                .filter(n -> WorkflowNodeUtils.isWaitNode(n.getNodeType()))
                .filter(n -> !"start".equalsIgnoreCase(n.getNodeType()))
                .collect(Collectors.toList());

        List<MigrationPlanVO.NodeMappingVO> result = new ArrayList<>();
        Set<String> matchedToIds = new HashSet<>();

        // 第一轮：按 nodeStatusCode 匹配
        for (WorkflowNode fromNode : fromWaitNodes) {
            String fromStatus = WorkflowNodeUtils.resolveNodeStatusCode(fromNode, false);
            MigrationPlanVO.NodeMappingVO mapping = new MigrationPlanVO.NodeMappingVO();
            mapping.setFromNodeId(fromNode.getNodeId());
            mapping.setFromNodeName(fromNode.getNodeName());
            mapping.setFromNodeType(fromNode.getNodeType());

            if (StringUtils.hasText(fromStatus)) {
                for (WorkflowNode toNode : toNodes) {
                    String toStatus = WorkflowNodeUtils.resolveNodeStatusCode(toNode, false);
                    if (fromStatus.equals(toStatus) && !matchedToIds.contains(toNode.getNodeId())) {
                        mapping.setToNodeId(toNode.getNodeId());
                        mapping.setToNodeName(toNode.getNodeName());
                        mapping.setToNodeType(toNode.getNodeType());
                        mapping.setAutoMatched(true);
                        matchedToIds.add(toNode.getNodeId());
                        break;
                    }
                }
            }
            result.add(mapping);
        }

        // 第二轮：按 nodeName 匹配（对未匹配的节点）
        for (MigrationPlanVO.NodeMappingVO mapping : result) {
            if (mapping.getToNodeId() != null) continue;

            for (WorkflowNode toNode : toNodes) {
                if (mapping.getFromNodeName().equals(toNode.getNodeName())
                        && !matchedToIds.contains(toNode.getNodeId())) {
                    mapping.setToNodeId(toNode.getNodeId());
                    mapping.setToNodeName(toNode.getNodeName());
                    mapping.setToNodeType(toNode.getNodeType());
                    mapping.setAutoMatched(true);
                    matchedToIds.add(toNode.getNodeId());
                    break;
                }
            }
        }

        return result;
    }

    private List<WorkflowMigrationPlan.NodeMappingItem> convertToEntityMapping(List<MigrationPlanVO.NodeMappingVO> voList) {
        return voList.stream().map(vo -> {
            WorkflowMigrationPlan.NodeMappingItem item = new WorkflowMigrationPlan.NodeMappingItem();
            item.setFromNodeId(vo.getFromNodeId());
            item.setToNodeId(vo.getToNodeId());
            item.setFromNodeName(vo.getFromNodeName());
            item.setToNodeName(vo.getToNodeName());
            return item;
        }).collect(Collectors.toList());
    }

    private List<MigrationPlanVO.NodeMappingVO> convertToVOMapping(List<WorkflowMigrationPlan.NodeMappingItem> items) {
        if (items == null) return Collections.emptyList();
        return items.stream().map(item -> {
            MigrationPlanVO.NodeMappingVO vo = new MigrationPlanVO.NodeMappingVO();
            vo.setFromNodeId(item.getFromNodeId());
            vo.setToNodeId(item.getToNodeId());
            vo.setFromNodeName(item.getFromNodeName());
            vo.setToNodeName(item.getToNodeName());
            vo.setAutoMatched(false);
            return vo;
        }).collect(Collectors.toList());
    }

    private MigrationPlanVO toPlanVO(WorkflowMigrationPlan plan, WorkflowVersion fromVersion,
                                      WorkflowVersion toVersion, List<MigrationPlanVO.NodeMappingVO> mapping) {
        MigrationPlanVO vo = new MigrationPlanVO();
        vo.setId(plan.getId());
        vo.setFromVersionId(plan.getFromVersionId());
        vo.setToVersionId(plan.getToVersionId());
        if (fromVersion != null) {
            vo.setFromVersionName(fromVersion.getName());
            vo.setFromVersion(fromVersion.getVersion());
        }
        if (toVersion != null) {
            vo.setToVersionName(toVersion.getName());
            vo.setToVersion(toVersion.getVersion());

            // 填充目标版本节点列表（供前端下拉选择）
            List<WorkflowNode> toNodes = workflowRuntimeLoader.loadNodes(toVersion.getId());
            List<MigrationPlanVO.TargetNodeOption> targetNodes = toNodes.stream()
                    .filter(n -> WorkflowNodeUtils.isWaitNode(n.getNodeType()))
                    .filter(n -> !"start".equalsIgnoreCase(n.getNodeType()))
                    .map(n -> {
                        MigrationPlanVO.TargetNodeOption opt = new MigrationPlanVO.TargetNodeOption();
                        opt.setNodeId(n.getNodeId());
                        opt.setNodeName(n.getNodeName());
                        opt.setNodeType(n.getNodeType());
                        return opt;
                    })
                    .collect(Collectors.toList());
            vo.setToVersionNodes(targetNodes);
        }
        vo.setProjectId(plan.getProjectId());
        vo.setNodeMapping(mapping);
        vo.setUnmappedNodes(mapping.stream().filter(m -> m.getToNodeId() == null && !m.isSkipped()).collect(Collectors.toList()));
        vo.setStatus(plan.getStatus());
        vo.setTotalInstanceCount(plan.getTotalInstanceCount());
        vo.setMigratedCount(plan.getMigratedCount());
        vo.setFailedCount(plan.getFailedCount());
        vo.setRemark(plan.getRemark());
        if (plan.getCreatedAt() != null) {
            vo.setCreatedAt(plan.getCreatedAt().toString());
        }

        // 填充操作人姓名
        if (plan.getOperatorId() != null) {
            try {
                SysUser operator = sysUserMapper.selectById(plan.getOperatorId());
                if (operator != null) {
                    vo.setOperatorName(StringUtils.hasText(operator.getRealName()) ? operator.getRealName() : operator.getUsername());
                }
            } catch (Exception ignored) {
                // 非关键字段，忽略异常
            }
        }

        return vo;
    }

}
