package com.demand.system.module.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.demand.system.common.exception.BusinessException;
import com.demand.system.common.result.ErrorCode;
import com.demand.system.module.auth.security.SecurityUtils;
import com.demand.system.module.requirement.entity.Requirement;
import com.demand.system.module.requirement.mapper.RequirementMapper;
import com.demand.system.module.user.entity.User;
import com.demand.system.module.user.mapper.UserMapper;
import com.demand.system.module.workflow.dto.CountersignRecordVO;
import com.demand.system.module.workflow.dto.CountersignSubmitDTO;
import com.demand.system.module.workflow.entity.WorkflowCountersignRecord;
import com.demand.system.module.workflow.entity.WorkflowInstance;
import com.demand.system.module.workflow.entity.WorkflowNode;
import com.demand.system.module.workflow.mapper.WorkflowCountersignRecordMapper;
import com.demand.system.module.workflow.mapper.WorkflowInstanceMapper;
import com.demand.system.module.workflow.mapper.WorkflowNodeMapper;
import com.demand.system.module.workflow.support.WorkflowNodeUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class WorkflowCountersignService {

    private final WorkflowCountersignRecordMapper countersignMapper;
    private final WorkflowInstanceMapper instanceMapper;
    private final WorkflowNodeMapper nodeMapper;
    private final RequirementMapper requirementMapper;
    private final UserMapper userMapper;
    private final WorkflowEngineService workflowEngineService;

    public WorkflowCountersignService(WorkflowCountersignRecordMapper countersignMapper,
                                     WorkflowInstanceMapper instanceMapper,
                                     WorkflowNodeMapper nodeMapper,
                                     RequirementMapper requirementMapper,
                                     UserMapper userMapper,
                                     @Lazy WorkflowEngineService workflowEngineService) {
        this.countersignMapper = countersignMapper;
        this.instanceMapper = instanceMapper;
        this.nodeMapper = nodeMapper;
        this.requirementMapper = requirementMapper;
        this.userMapper = userMapper;
        this.workflowEngineService = workflowEngineService;
    }

    /**
     * 初始化会签记录
     */
    @Transactional
    public void initCountersignRecords(Long instanceId, String nodeId, List<Long> approverIds) {
        for (Long approverId : approverIds) {
            WorkflowCountersignRecord record = new WorkflowCountersignRecord();
            record.setInstanceId(instanceId);
            record.setNodeId(nodeId);
            record.setApproverId(approverId);
            record.setStatus("pending");
            countersignMapper.insert(record);
        }
    }

    /**
     * 提交会签审批
     */
    @Transactional
    public void submitCountersignApproval(CountersignSubmitDTO dto) {
        Long approverId = SecurityUtils.getCurrentUserId();

        Requirement requirement = requirementMapper.selectById(dto.getRequirementId());
        if (requirement == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "需求不存在");
        }

        WorkflowInstance instance = instanceMapper.selectOne(
            new LambdaQueryWrapper<WorkflowInstance>()
                .eq(WorkflowInstance::getRequirementId, dto.getRequirementId())
        );
        if (instance == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "工作流实例不存在");
        }

        WorkflowNode node = nodeMapper.selectOne(
            new LambdaQueryWrapper<WorkflowNode>()
                .eq(WorkflowNode::getWorkflowVersionId, instance.getWorkflowVersionId())
                .eq(WorkflowNode::getNodeId, dto.getNodeId())
        );
        if (node == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "节点不存在");
        }

        // 检查是否已审批
        WorkflowCountersignRecord existing = countersignMapper.selectOne(
            new LambdaQueryWrapper<WorkflowCountersignRecord>()
                .eq(WorkflowCountersignRecord::getInstanceId, instance.getId())
                .eq(WorkflowCountersignRecord::getNodeId, dto.getNodeId())
                .eq(WorkflowCountersignRecord::getApproverId, approverId)
        );
        if (existing != null && !"pending".equals(existing.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "您已完成审批，无需重复提交");
        }

        // 验证评分
        if (dto.getRating() != null && (dto.getRating() < 1 || dto.getRating() > 5)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "评分必须在1-5之间");
        }

        // 更新或创建会签记录
        if (existing != null) {
            existing.setStatus(dto.getStatus());
            existing.setRating(dto.getRating());
            existing.setComment(dto.getComment());
            existing.setApprovedAt(LocalDateTime.now());
            countersignMapper.updateById(existing);
        } else {
            WorkflowCountersignRecord record = new WorkflowCountersignRecord();
            record.setInstanceId(instance.getId());
            record.setNodeId(dto.getNodeId());
            record.setApproverId(approverId);
            record.setStatus(dto.getStatus());
            record.setRating(dto.getRating());
            record.setComment(dto.getComment());
            record.setApprovedAt(LocalDateTime.now());
            countersignMapper.insert(record);
        }

        // 检查是否满足流转条件
        if (canProceedAfterCountersign(instance.getId(), dto.getNodeId(), node)) {
            workflowEngineService.autoTransitionAfterCountersign(dto.getRequirementId(), dto.getNodeId());
        }
    }

    /**
     * 检查会签是否完成
     */
    public boolean canProceedAfterCountersign(Long instanceId, String nodeId, WorkflowNode node) {
        Map<String, Object> properties = node.getProperties();
        if (properties == null) {
            return false;
        }

        Boolean countersignEnabled = (Boolean) properties.get("countersignEnabled");
        if (countersignEnabled == null || !countersignEnabled) {
            return false;
        }

        String strategy = (String) properties.get("countersignStrategy");
        if (strategy == null) {
            strategy = "ALL";
        }

        List<WorkflowCountersignRecord> records = countersignMapper.selectList(
            new LambdaQueryWrapper<WorkflowCountersignRecord>()
                .eq(WorkflowCountersignRecord::getInstanceId, instanceId)
                .eq(WorkflowCountersignRecord::getNodeId, nodeId)
        );

        if (records.isEmpty()) {
            return false;
        }

        long approvedCount = records.stream().filter(r -> "approved".equals(r.getStatus())).count();
        long rejectedCount = records.stream().filter(r -> "rejected".equals(r.getStatus())).count();
        long totalCount = records.size();

        switch (strategy) {
            case "ALL":
                return approvedCount == totalCount;
            case "ANY":
                return approvedCount > 0;
            case "MAJORITY":
                return approvedCount > totalCount / 2;
            default:
                return false;
        }
    }

    /**
     * 获取会签记录列表
     */
    public List<CountersignRecordVO> getCountersignRecords(Long requirementId, String nodeId) {
        WorkflowInstance instance = instanceMapper.selectOne(
            new LambdaQueryWrapper<WorkflowInstance>()
                .eq(WorkflowInstance::getRequirementId, requirementId)
        );
        if (instance == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "工作流实例不存在");
        }

        List<WorkflowCountersignRecord> records = countersignMapper.selectList(
            new LambdaQueryWrapper<WorkflowCountersignRecord>()
                .eq(WorkflowCountersignRecord::getInstanceId, instance.getId())
                .eq(WorkflowCountersignRecord::getNodeId, nodeId)
                .orderByAsc(WorkflowCountersignRecord::getCreatedAt)
        );

        return records.stream().map(record -> {
            CountersignRecordVO vo = new CountersignRecordVO();
            vo.setId(record.getId());
            vo.setInstanceId(record.getInstanceId());
            vo.setNodeId(record.getNodeId());
            vo.setApproverId(record.getApproverId());
            vo.setStatus(record.getStatus());
            vo.setRating(record.getRating());
            vo.setComment(record.getComment());
            vo.setApprovedAt(record.getApprovedAt());
            vo.setCreatedAt(record.getCreatedAt());

            if (record.getApproverId() != null) {
                User user = userMapper.selectById(record.getApproverId());
                if (user != null) {
                    vo.setApproverName(user.getRealName());
                }
            }

            return vo;
        }).collect(Collectors.toList());
    }

    /**
     * 检查当前用户是否可以会签
     */
    public boolean canCurrentUserCountersign(Long requirementId, String nodeId) {
        Long userId = SecurityUtils.getCurrentUserId();

        WorkflowInstance instance = instanceMapper.selectOne(
            new LambdaQueryWrapper<WorkflowInstance>()
                .eq(WorkflowInstance::getRequirementId, requirementId)
        );
        if (instance == null) {
            return false;
        }

        WorkflowCountersignRecord record = countersignMapper.selectOne(
            new LambdaQueryWrapper<WorkflowCountersignRecord>()
                .eq(WorkflowCountersignRecord::getInstanceId, instance.getId())
                .eq(WorkflowCountersignRecord::getNodeId, nodeId)
                .eq(WorkflowCountersignRecord::getApproverId, userId)
        );

        return record != null && "pending".equals(record.getStatus());
    }
}
