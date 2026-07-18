package com.demand.system.module.bitable.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.demand.system.common.exception.BusinessException;
import com.demand.system.module.bitable.converter.BitableConverter;
import com.demand.system.module.bitable.dto.BitableTableCreateDTO;
import com.demand.system.module.bitable.dto.BitableTableUpdateDTO;
import com.demand.system.module.bitable.dto.BitableTableVO;
import com.demand.system.module.bitable.entity.BitableView;
import com.demand.system.module.bitable.entity.BitableTable;
import com.demand.system.module.bitable.entity.BitableField;
import com.demand.system.module.bitable.entity.BitableComment;
import com.demand.system.module.bitable.mapper.BitableTableMapper;
import com.demand.system.module.bitable.mapper.BitableFieldMapper;
import com.demand.system.module.bitable.mapper.BitableRecordMapper;
import com.demand.system.module.bitable.mapper.BitableCellMapper;
import com.demand.system.module.bitable.mapper.BitableViewMapper;
import com.demand.system.module.bitable.mapper.BitableCommentMapper;
import com.demand.system.module.bitable.service.BitableTableService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 多维表格-数据表 Service 实现
 */
@Service
public class BitableTableServiceImpl implements BitableTableService {

    private final BitableTableMapper tableMapper;
    private final BitableFieldMapper fieldMapper;
    private final BitableRecordMapper recordMapper;
    private final BitableCellMapper cellMapper;
    private final BitableViewMapper viewMapper;
    private final BitableCommentMapper commentMapper;
    private final BitableConverter converter;

    public BitableTableServiceImpl(BitableTableMapper tableMapper,
                                   BitableFieldMapper fieldMapper,
                                   BitableRecordMapper recordMapper,
                                   BitableCellMapper cellMapper,
                                   BitableViewMapper viewMapper,
                                   BitableCommentMapper commentMapper,
                                   BitableConverter converter) {
        this.tableMapper = tableMapper;
        this.fieldMapper = fieldMapper;
        this.recordMapper = recordMapper;
        this.cellMapper = cellMapper;
        this.viewMapper = viewMapper;
        this.commentMapper = commentMapper;
        this.converter = converter;
    }

    @Override
    public List<BitableTableVO> listTables(Long baseId) {
        List<BitableTable> tables = tableMapper.selectByBaseId(baseId);
        List<BitableTableVO> voList = converter.toTableVOList(tables);
        for (BitableTableVO vo : voList) {
            vo.setRecordCount(recordMapper.countByTableId(vo.getId()));
            vo.setFieldCount(fieldMapper.countByTableId(vo.getId()));
        }
        return voList;
    }

    @Override
    public BitableTableVO getTableById(Long id) {
        BitableTable table = tableMapper.selectById(id);
        if (table == null) {
            throw new BusinessException("数据表不存在");
        }
        BitableTableVO vo = converter.toTableVO(table);
        vo.setRecordCount(recordMapper.countByTableId(id));
        vo.setFieldCount(fieldMapper.countByTableId(id));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createTable(Long baseId, BitableTableCreateDTO dto, Long userId) {
        BitableTable table = new BitableTable();
        BeanUtils.copyProperties(dto, table);
        table.setBaseId(baseId);
        table.setSortOrder(0);
        tableMapper.insert(table);

        // 自动创建一个默认 grid 视图
        BitableView defaultView = new BitableView();
        defaultView.setTableId(table.getId());
        defaultView.setName("默认视图");
        defaultView.setViewType("grid");
        defaultView.setSortOrder(0);
        defaultView.setVersion(0);
        defaultView.setCreatedBy(userId);
        viewMapper.insert(defaultView);

        // 设置默认视图ID
        table.setDefaultViewId(defaultView.getId());
        UpdateWrapper<BitableTable> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("id", table.getId())
                .set("default_view_id", defaultView.getId());
        tableMapper.update(null, updateWrapper);

        return table.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateTable(Long id, BitableTableUpdateDTO dto) {
        BitableTable existing = tableMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("数据表不存在");
        }

        UpdateWrapper<BitableTable> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", id);
        if (dto.getName() != null) {
            wrapper.set("name", dto.getName());
        }
        if (dto.getDescription() != null) {
            wrapper.set("description", dto.getDescription());
        }
        if (dto.getIcon() != null) {
            wrapper.set("icon", dto.getIcon());
        }
        if (dto.getSortOrder() != null) {
            wrapper.set("sort_order", dto.getSortOrder());
        }
        tableMapper.update(null, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTable(Long id) {
        BitableTable existing = tableMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("数据表不存在");
        }

        // 级联删除：字段(软删) / 记录(物理删) / 单元格(物理删) / 视图(软删) / 评论(软删)
        fieldMapper.delete(new LambdaQueryWrapper<BitableField>()
                .eq(BitableField::getTableId, id));
        recordMapper.deleteByTableId(id);
        viewMapper.delete(new LambdaQueryWrapper<BitableView>()
                .eq(BitableView::getTableId, id));
        commentMapper.delete(new LambdaQueryWrapper<BitableComment>()
                .eq(BitableComment::getTableId, id));

        // 软删数据表
        tableMapper.deleteById(id);
    }
}