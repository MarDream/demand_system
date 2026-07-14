package com.demand.system.module.bitable.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.demand.system.common.exception.BusinessException;
import com.demand.system.common.result.ErrorCode;
import com.demand.system.common.result.PageResult;
import com.demand.system.common.util.UserNameResolver;
import com.demand.system.module.bitable.converter.BitableConverter;
import com.demand.system.module.bitable.dto.BitableCellValueVO;
import com.demand.system.module.bitable.dto.BitableRecordCreateDTO;
import com.demand.system.module.bitable.dto.BitableRecordVO;
import com.demand.system.module.bitable.dto.CellValueDTO;
import com.demand.system.module.bitable.entity.BitableCellValue;
import com.demand.system.module.bitable.entity.BitableComment;
import com.demand.system.module.bitable.entity.BitableRecord;
import com.demand.system.module.bitable.mapper.BitableCellMapper;
import com.demand.system.module.bitable.mapper.BitableCommentMapper;
import com.demand.system.module.bitable.mapper.BitableRecordMapper;
import com.demand.system.module.bitable.service.BitableRecordService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 多维表格-记录行 Service 实现
 */
@Service
public class BitableRecordServiceImpl implements BitableRecordService {

    private final BitableRecordMapper recordMapper;
    private final BitableCellMapper cellMapper;
    private final BitableCommentMapper commentMapper;
    private final BitableConverter converter;
    private final UserNameResolver userNameResolver;

    public BitableRecordServiceImpl(BitableRecordMapper recordMapper,
                                    BitableCellMapper cellMapper,
                                    BitableCommentMapper commentMapper,
                                    BitableConverter converter,
                                    UserNameResolver userNameResolver) {
        this.recordMapper = recordMapper;
        this.cellMapper = cellMapper;
        this.commentMapper = commentMapper;
        this.converter = converter;
        this.userNameResolver = userNameResolver;
    }

    @Override
    public PageResult<BitableRecordVO> listRecords(Long tableId, Integer pageNum, Integer pageSize) {
        int total = recordMapper.countByTableId(tableId);
        int offset = (pageNum - 1) * pageSize;

        List<BitableRecord> records = recordMapper.selectByTableId(tableId, offset, pageSize);
        List<BitableRecordVO> voList = converter.toRecordVOList(records);

        // 批量查询这些 records 的 cell_values
        if (!records.isEmpty()) {
            List<Long> recordIds = records.stream().map(BitableRecord::getId).collect(Collectors.toList());
            List<BitableCellValue> cells = cellMapper.selectByRecordIds(recordIds);
            List<BitableCellValueVO> cellVOs = converter.toCellValueVOList(cells);

            // 组装成 Map<recordId, Map<fieldId, CellValueVO>>
            Map<Long, Map<Long, BitableCellValueVO>> recordCellsMap = new LinkedHashMap<>();
            for (BitableCellValueVO cellVO : cellVOs) {
                recordCellsMap.computeIfAbsent(cellVO.getRecordId(), k -> new LinkedHashMap<>())
                        .put(cellVO.getFieldId(), cellVO);
            }

            // 填充 VO 的 cells 和 createdByName/updatedByName
            for (BitableRecordVO vo : voList) {
                vo.setCells(recordCellsMap.getOrDefault(vo.getId(), new LinkedHashMap<>()));
                vo.setCreatedByName(userNameResolver.resolveUserName(vo.getCreatedBy(), "未知用户"));
                vo.setUpdatedByName(userNameResolver.resolveUserName(vo.getUpdatedBy(), "未知用户"));
            }
        }

        return new PageResult<>(voList, total, pageNum, pageSize);
    }

    @Override
    public BitableRecordVO getRecordById(Long id) {
        BitableRecord record = recordMapper.selectById(id);
        if (record == null) {
            throw new BusinessException("记录不存在");
        }

        BitableRecordVO vo = converter.toRecordVO(record);

        // 查询该记录的所有单元格值
        List<BitableCellValue> cells = cellMapper.selectByRecordIds(List.of(id));
        Map<Long, BitableCellValueVO> cellsMap = new LinkedHashMap<>();
        for (BitableCellValue cell : cells) {
            BitableCellValueVO cellVO = converter.toCellValueVO(cell);
            cellsMap.put(cellVO.getFieldId(), cellVO);
        }
        vo.setCells(cellsMap);
        vo.setCreatedByName(userNameResolver.resolveUserName(record.getCreatedBy(), "未知用户"));
        vo.setUpdatedByName(userNameResolver.resolveUserName(record.getUpdatedBy(), "未知用户"));

        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createRecord(Long tableId, BitableRecordCreateDTO dto, Long userId) {
        BitableRecord record = new BitableRecord();
        record.setTableId(tableId);
        record.setCreatedBy(userId);
        record.setUpdatedBy(userId);
        record.setSortOrder(0);
        record.setVersion(0);
        recordMapper.insert(record);

        // 为每个 fieldId 创建 BitableCellValue
        if (dto.getCells() != null && !dto.getCells().isEmpty()) {
            for (Map.Entry<Long, CellValueDTO> entry : dto.getCells().entrySet()) {
                Long fieldId = entry.getKey();
                CellValueDTO cellDTO = entry.getValue();

                BitableCellValue cell = new BitableCellValue();
                cell.setRecordId(record.getId());
                cell.setFieldId(fieldId);
                BeanUtils.copyProperties(cellDTO, cell);
                cellMapper.saveOrUpdateCell(cell);
            }
        }

        return record.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRecord(Long id, BitableRecordCreateDTO dto, Long userId) {
        BitableRecord existing = recordMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("记录不存在");
        }

        // 乐观锁更新 record
        UpdateWrapper<BitableRecord> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", id)
                .eq("version", existing.getVersion())
                .set("updated_by", userId)
                .set("version", existing.getVersion() + 1);

        int updated = recordMapper.update(null, wrapper);
        if (updated <= 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "记录已被他人修改，请刷新后重试");
        }

        // 更新所有 cells
        if (dto.getCells() != null && !dto.getCells().isEmpty()) {
            for (Map.Entry<Long, CellValueDTO> entry : dto.getCells().entrySet()) {
                Long fieldId = entry.getKey();
                CellValueDTO cellDTO = entry.getValue();

                BitableCellValue cell = new BitableCellValue();
                cell.setRecordId(id);
                cell.setFieldId(fieldId);
                BeanUtils.copyProperties(cellDTO, cell);
                cellMapper.saveOrUpdateCell(cell);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRecord(Long id) {
        BitableRecord existing = recordMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("记录不存在");
        }

        // 物理删 cell_values
        cellMapper.deleteByRecordId(id);

        // 软删 comments（@TableLogic 自动处理）
        LambdaQueryWrapper<BitableComment> commentWrapper = new LambdaQueryWrapper<>();
        commentWrapper.eq(BitableComment::getRecordId, id);
        commentMapper.delete(commentWrapper);

        // 软删 record（@TableLogic 自动设置 deleted_at=1）
        recordMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long batchCreateRecords(Long tableId, List<BitableRecordCreateDTO> dtos, Long userId) {
        Long lastId = null;
        for (BitableRecordCreateDTO dto : dtos) {
            lastId = createRecord(tableId, dto, userId);
        }
        return lastId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer updateCell(Long recordId, Long fieldId, Object value, Integer version, Long userId) {
        // 1. 查询 Record 确认存在且 version 匹配
        BitableRecord existing = recordMapper.selectById(recordId);
        if (existing == null) {
            throw new BusinessException("记录不存在");
        }
        if (!existing.getVersion().equals(version)) {
            throw new BusinessException(ErrorCode.CONFLICT, "记录已被他人修改，请刷新后重试");
        }

        // 2. 乐观锁更新 Record 的 updated_by 和 version
        UpdateWrapper<BitableRecord> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", recordId)
                .eq("version", version)
                .set("updated_by", userId)
                .set("version", version + 1);

        int updated = recordMapper.update(null, wrapper);
        if (updated <= 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "记录已被他人修改，请刷新后重试");
        }

        // 3. 插入或更新 CellValue
        BitableCellValue cell = new BitableCellValue();
        cell.setRecordId(recordId);
        cell.setFieldId(fieldId);
        if (value instanceof CellValueDTO) {
            BeanUtils.copyProperties(value, cell);
        } else if (value instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) value;
            if (map.containsKey("valueText")) {
                cell.setValueText((String) map.get("valueText"));
            }
            if (map.containsKey("valueNumber")) {
                Object num = map.get("valueNumber");
                if (num instanceof Number) {
                    cell.setValueNumber(new java.math.BigDecimal(num.toString()));
                }
            }
            if (map.containsKey("valueDate")) {
                Object date = map.get("valueDate");
                if (date instanceof String) {
                    cell.setValueDate(java.time.LocalDate.parse((String) date));
                } else if (date instanceof java.time.LocalDate) {
                    cell.setValueDate((java.time.LocalDate) date);
                }
            }
            if (map.containsKey("valueJson")) {
                cell.setValueJson((String) map.get("valueJson"));
            }
        } else if (value instanceof String) {
            cell.setValueText((String) value);
        } else if (value instanceof Number) {
            cell.setValueNumber(new java.math.BigDecimal(value.toString()));
        }
        cellMapper.saveOrUpdateCell(cell);

        // 4. 返回新版本号
        return version + 1;
    }
}