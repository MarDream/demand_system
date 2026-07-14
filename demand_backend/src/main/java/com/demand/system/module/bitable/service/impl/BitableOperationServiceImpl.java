package com.demand.system.module.bitable.service.impl;

import com.demand.system.common.result.PageResult;
import com.demand.system.common.util.UserNameResolver;
import com.demand.system.module.bitable.constant.OperationType;
import com.demand.system.module.bitable.dto.BitableOperationVO;
import com.demand.system.module.bitable.entity.BitableOperation;
import com.demand.system.module.bitable.mapper.BitableOperationMapper;
import com.demand.system.module.bitable.service.BitableOperationService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 多维表格操作历史 Service 实现
 */
@Service
public class BitableOperationServiceImpl implements BitableOperationService {

    private final BitableOperationMapper operationMapper;
    private final UserNameResolver userNameResolver;

    public BitableOperationServiceImpl(BitableOperationMapper operationMapper,
                                       UserNameResolver userNameResolver) {
        this.operationMapper = operationMapper;
        this.userNameResolver = userNameResolver;
    }

    @Override
    public void recordOperation(Long baseId, Long tableId, Long userId, String operationType, String detail) {
        BitableOperation operation = new BitableOperation();
        operation.setBaseId(baseId);
        operation.setTableId(tableId);
        operation.setUserId(userId);
        operation.setOperationType(operationType);
        operation.setDetail(detail);
        operationMapper.insert(operation);
    }

    @Override
    public PageResult<BitableOperationVO> listOperationsByBaseId(Long baseId, Integer pageNum, Integer pageSize) {
        int total = operationMapper.countByBaseId(baseId);
        int offset = (pageNum - 1) * pageSize;

        List<BitableOperation> operations = operationMapper.selectByBaseId(baseId, offset, pageSize);
        List<BitableOperationVO> voList = operations.stream()
                .map(this::toOperationVO)
                .collect(Collectors.toList());

        return new PageResult<>(voList, total, pageNum, pageSize);
    }

    @Override
    public PageResult<BitableOperationVO> listOperationsByTableId(Long baseId, Long tableId, Integer pageNum, Integer pageSize) {
        int total = operationMapper.countByTableId(baseId, tableId);
        int offset = (pageNum - 1) * pageSize;

        List<BitableOperation> operations = operationMapper.selectByTableId(baseId, tableId, offset, pageSize);
        List<BitableOperationVO> voList = operations.stream()
                .map(this::toOperationVO)
                .collect(Collectors.toList());

        return new PageResult<>(voList, total, pageNum, pageSize);
    }

    private BitableOperationVO toOperationVO(BitableOperation operation) {
        BitableOperationVO vo = new BitableOperationVO();
        vo.setId(operation.getId());
        vo.setBaseId(operation.getBaseId());
        vo.setTableId(operation.getTableId());
        vo.setUserId(operation.getUserId());
        vo.setUserName(userNameResolver.resolveUserName(operation.getUserId(), "未知用户"));
        vo.setOperationType(operation.getOperationType());
        vo.setDetail(operation.getDetail());
        vo.setCreatedAt(operation.getCreatedAt());

        // 解析操作类型标签
        OperationType type = OperationType.fromCode(operation.getOperationType());
        if (type != null) {
            vo.setOperationTypeLabel(type.getLabel());
        } else {
            vo.setOperationTypeLabel(operation.getOperationType());
        }

        return vo;
    }
}