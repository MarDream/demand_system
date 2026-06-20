package com.demand.system.module.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.demand.system.common.exception.BusinessException;
import com.demand.system.common.result.ErrorCode;
import com.demand.system.module.auth.security.SecurityUtils;
import com.demand.system.module.requirement.entity.Requirement;
import com.demand.system.module.requirement.mapper.RequirementMapper;
import com.demand.system.module.workflow.entity.WorkflowInstance;
import com.demand.system.module.workflow.entity.WorkflowMigrationLog;
import com.demand.system.module.workflow.entity.WorkflowVersion;
import com.demand.system.module.workflow.mapper.WorkflowInstanceMapper;
import com.demand.system.module.workflow.mapper.WorkflowMigrationLogMapper;
import com.demand.system.module.workflow.mapper.WorkflowVersionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 工作流版本迁移服务
 */
@Service
public class WorkflowMigrationService {

    private final WorkflowInstanceMapper workflowInstanceMapper;
    private final WorkflowVersionMapper workflowVersionMapper;
    private final WorkflowMigrationLogMapper workflowMigrationLogMapper;
    private final RequirementMapper requirementMapper;

    public WorkflowMigrationService(WorkflowInstanceMapper workflowInstanceMapper,
                                   WorkflowVersionMapper workflowVersionMapper,
                                   WorkflowMigrationLogMapper workflowMigrationLogMapper,
                                   RequirementMapper requirementMapper) {
        this.workflowInstanceMapper = workflowInstanceMapper;
        this.workflowVersionMapper = workflowVersionMapper;
        this.workflowMigrationLogMapper = workflowMigrationLogMapper;
        this.requirementMapper = requirementMapper;
    }

    /**
     * 批量迁移工作流版本
     *
     * @param fromVersionId 源版本ID
     * @param toVersionId 目标版本ID
     * @return 迁移结果统计
     */
    @Transactional
    public MigrationResult batchMigrate(Long fromVersionId, Long toVersionId) {
        Long operatorId = SecurityUtils.getCurrentUserId();

        // 1. 校验版本存在性
        WorkflowVersion fromVersion = workflowVersionMapper.selectById(fromVersionId);
        WorkflowVersion toVersion = workflowVersionMapper.selectById(toVersionId);

        if (fromVersion == null || toVersion == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "工作流版本不存在");
        }

        if (!fromVersion.getProjectId().equals(toVersion.getProjectId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "源版本与目标版本必须属于同一项目");
        }

        if (toVersion.getIsActive() != 1) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "目标版本必须是已启用状态");
        }

        // 2. 查询需要迁移的工作流实例（运行中状态）
        List<WorkflowInstance> instances = workflowInstanceMapper.selectList(
                new LambdaQueryWrapper<WorkflowInstance>()
                        .eq(WorkflowInstance::getWorkflowVersionId, fromVersionId)
                        .eq(WorkflowInstance::getStatus, "running")
        );

        if (instances.isEmpty()) {
            return new MigrationResult(0, 0, 0, "无需迁移的工单");
        }

        // 3. 批量迁移
        int successCount = 0;
        int failedCount = 0;
        List<String> failedReasons = new ArrayList<>();

        for (WorkflowInstance instance : instances) {
            try {
                migrateInstance(instance, fromVersionId, toVersionId, operatorId);
                successCount++;
            } catch (Exception e) {
                failedCount++;
                failedReasons.add(String.format("需求#%d: %s", instance.getRequirementId(), e.getMessage()));

                // 记录失败日志
                WorkflowMigrationLog log = new WorkflowMigrationLog();
                log.setFromVersionId(fromVersionId);
                log.setToVersionId(toVersionId);
                log.setRequirementId(instance.getRequirementId());
                log.setMigrationType("batch");
                log.setMigrationStatus("failed");
                log.setErrorMessage(e.getMessage());
                log.setOperatorId(operatorId);
                workflowMigrationLogMapper.insert(log);
            }
        }

        String message = String.format("迁移完成：成功 %d 个，失败 %d 个", successCount, failedCount);
        if (!failedReasons.isEmpty()) {
            message += "\n失败原因：\n" + String.join("\n", failedReasons);
        }

        return new MigrationResult(instances.size(), successCount, failedCount, message);
    }

    /**
     * 单个实例迁移
     */
    private void migrateInstance(WorkflowInstance instance, Long fromVersionId, Long toVersionId, Long operatorId) {
        // 1. 更新工作流实例版本
        int updated = workflowInstanceMapper.update(null, new LambdaUpdateWrapper<WorkflowInstance>()
                .eq(WorkflowInstance::getId, instance.getId())
                .eq(WorkflowInstance::getWorkflowVersionId, fromVersionId) // 乐观锁
                .set(WorkflowInstance::getWorkflowVersionId, toVersionId)
        );

        if (updated <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "工作流实例已被修改，请刷新后重试");
        }

        // 2. 记录成功日志
        WorkflowMigrationLog log = new WorkflowMigrationLog();
        log.setFromVersionId(fromVersionId);
        log.setToVersionId(toVersionId);
        log.setRequirementId(instance.getRequirementId());
        log.setMigrationType("batch");
        log.setMigrationStatus("success");
        log.setOperatorId(operatorId);
        workflowMigrationLogMapper.insert(log);
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

    /**
     * 迁移结果统计
     */
    public static class MigrationResult {
        private Integer totalCount;
        private Integer successCount;
        private Integer failedCount;
        private String message;

        public MigrationResult(Integer totalCount, Integer successCount, Integer failedCount, String message) {
            this.totalCount = totalCount;
            this.successCount = successCount;
            this.failedCount = failedCount;
            this.message = message;
        }

        public Integer getTotalCount() {
            return totalCount;
        }

        public Integer getSuccessCount() {
            return successCount;
        }

        public Integer getFailedCount() {
            return failedCount;
        }

        public String getMessage() {
            return message;
        }
    }
}
