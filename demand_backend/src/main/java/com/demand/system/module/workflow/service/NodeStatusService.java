package com.demand.system.module.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.demand.system.common.exception.BusinessException;
import com.demand.system.common.result.ErrorCode;
import com.demand.system.module.requirement.dto.SortRequest;
import com.demand.system.module.workflow.entity.NodeStatus;
import com.demand.system.module.workflow.entity.WorkflowNode;
import com.demand.system.module.workflow.mapper.NodeStatusMapper;
import com.demand.system.module.workflow.mapper.WorkflowNodeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NodeStatusService {

    private static final Map<String, String> BUILT_IN_STATUS_NAMES = Map.ofEntries(
            Map.entry("DRAFT", "新建"),
            Map.entry("PENDING_ANALYSIS", "待分析"),
            Map.entry("PENDING_CONFIRM", "待确认"),
            Map.entry("PENDING_REVIEW", "待评审"),
            Map.entry("IN_DEVELOPMENT", "开发中"),
            Map.entry("IN_TESTING", "测试中"),
            Map.entry("LIVE", "已上线"),
            Map.entry("ACCEPTED", "已验收"),
            Map.entry("CANCELLED", "已取消")
    );

    private final NodeStatusMapper nodeStatusMapper;
    private final WorkflowNodeMapper workflowNodeMapper;

    public List<NodeStatus> list() {
        List<NodeStatus> statuses = nodeStatusMapper.selectList(
            new LambdaQueryWrapper<NodeStatus>().orderByAsc(NodeStatus::getSortOrder)
        );
        repairGarbledBuiltInNames(statuses);
        return statuses;
    }

    @Transactional
    public void create(NodeStatus nodeStatus) {
        validateCodeUnique(nodeStatus.getCode(), null);
        if (nodeStatus.getSortOrder() == null) {
            nodeStatus.setSortOrder(nextSortOrder());
        }
        nodeStatusMapper.insert(nodeStatus);
    }

    @Transactional
    public void update(NodeStatus nodeStatus) {
        validateCodeUnique(nodeStatus.getCode(), nodeStatus.getId());
        nodeStatusMapper.updateById(nodeStatus);
    }

    @Transactional
    public void delete(Long id) {
        NodeStatus existing = nodeStatusMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "节点状态不存在");
        }
        validateNotUsedByWorkflow(existing.getCode());
        nodeStatusMapper.deleteById(id);
    }

    @Transactional
    public List<NodeStatus> sort(List<SortRequest> sortRequests) {
        if (CollectionUtils.isEmpty(sortRequests)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "排序列表不能为空");
        }
        List<Long> ids = sortRequests.stream().map(SortRequest::getId).toList();
        List<NodeStatus> statuses = nodeStatusMapper.selectBatchIds(ids);
        if (statuses.size() != sortRequests.size()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "部分节点状态不存在");
        }
        Map<Long, Integer> sortOrderMap = sortRequests.stream()
                .collect(Collectors.toMap(SortRequest::getId, SortRequest::getSortOrder));
        statuses.forEach(status -> {
            status.setSortOrder(sortOrderMap.get(status.getId()));
            nodeStatusMapper.updateById(status);
        });
        return list();
    }

    private void validateCodeUnique(String code, Long excludeId) {
        LambdaQueryWrapper<NodeStatus> wrapper = new LambdaQueryWrapper<NodeStatus>()
            .eq(NodeStatus::getCode, code);
        if (excludeId != null) {
            wrapper.ne(NodeStatus::getId, excludeId);
        }
        if (nodeStatusMapper.selectCount(wrapper) > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "状态编码已存在: " + code);
        }
    }

    private Integer nextSortOrder() {
        NodeStatus last = nodeStatusMapper.selectOne(
                new LambdaQueryWrapper<NodeStatus>()
                        .orderByDesc(NodeStatus::getSortOrder)
                        .last("LIMIT 1")
        );
        return last == null || last.getSortOrder() == null ? 0 : last.getSortOrder() + 1;
    }

    private void repairGarbledBuiltInNames(List<NodeStatus> statuses) {
        statuses.forEach(status -> {
            String builtInName = BUILT_IN_STATUS_NAMES.get(status.getCode());
            if (builtInName == null || builtInName.equals(status.getName()) || !isGarbledName(status.getName())) {
                return;
            }
            status.setName(builtInName);
            nodeStatusMapper.updateById(status);
        });
    }

    private boolean isGarbledName(String name) {
        if (!StringUtils.hasText(name)) {
            return true;
        }
        boolean hasChinese = name.codePoints().anyMatch(codePoint ->
                codePoint >= 0x4E00 && codePoint <= 0x9FFF
        );
        if (hasChinese) {
            return false;
        }
        return name.indexOf('Ã') >= 0
                || name.indexOf('Â') >= 0
                || name.indexOf('æ') >= 0
                || name.indexOf('å') >= 0
                || name.indexOf('ä') >= 0
                || name.indexOf('ç') >= 0
                || name.indexOf('è') >= 0
                || name.indexOf('é') >= 0
                || name.indexOf('œ') >= 0
                || name.indexOf('º') >= 0
                || name.indexOf('»') >= 0
                || name.indexOf('¼') >= 0
                || name.indexOf('½') >= 0
                || name.indexOf('¾') >= 0
                || name.indexOf('¿') >= 0;
    }

    private void validateNotUsedByWorkflow(String statusCode) {
        long count = workflowNodeMapper.selectCount(
                new LambdaQueryWrapper<WorkflowNode>()
                        .apply(
                                "JSON_UNQUOTE(JSON_EXTRACT(properties, '$.nodeStatusCode')) = {0} " +
                                        "OR JSON_UNQUOTE(JSON_EXTRACT(properties, '$.statusCode')) = {0}",
                                statusCode
                        )
        );
        if (count > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该节点状态已被工作流节点引用，无法删除");
        }
    }
}
