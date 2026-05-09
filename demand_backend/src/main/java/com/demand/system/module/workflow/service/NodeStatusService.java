package com.demand.system.module.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.demand.system.common.exception.BusinessException;
import com.demand.system.common.result.ErrorCode;
import com.demand.system.module.workflow.entity.NodeStatus;
import com.demand.system.module.workflow.mapper.NodeStatusMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NodeStatusService {

    private final NodeStatusMapper nodeStatusMapper;

    public List<NodeStatus> list() {
        return nodeStatusMapper.selectList(
            new LambdaQueryWrapper<NodeStatus>().orderByAsc(NodeStatus::getSortOrder)
        );
    }

    public void create(NodeStatus nodeStatus) {
        validateCodeUnique(nodeStatus.getCode(), null);
        nodeStatusMapper.insert(nodeStatus);
    }

    public void update(NodeStatus nodeStatus) {
        validateCodeUnique(nodeStatus.getCode(), nodeStatus.getId());
        nodeStatusMapper.updateById(nodeStatus);
    }

    public void delete(Long id) {
        NodeStatus existing = nodeStatusMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "节点状态不存在");
        }
        nodeStatusMapper.deleteById(id);
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
}
