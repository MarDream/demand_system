package com.demand.system.module.bitable.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.demand.system.common.exception.BusinessException;
import com.demand.system.module.bitable.constant.ViewType;
import com.demand.system.module.bitable.converter.BitableConverter;
import com.demand.system.module.bitable.dto.BitableViewCreateDTO;
import com.demand.system.module.bitable.dto.BitableViewUpdateDTO;
import com.demand.system.module.bitable.dto.BitableViewVO;
import com.demand.system.module.bitable.entity.BitableView;
import com.demand.system.module.bitable.mapper.BitableViewMapper;
import com.demand.system.module.bitable.service.BitableViewService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 多维表格-视图定义 Service 实现
 */
@Service
public class BitableViewServiceImpl implements BitableViewService {

    private final BitableViewMapper viewMapper;
    private final BitableConverter converter;

    public BitableViewServiceImpl(BitableViewMapper viewMapper, BitableConverter converter) {
        this.viewMapper = viewMapper;
        this.converter = converter;
    }

    @Override
    public List<BitableViewVO> listViews(Long tableId) {
        List<BitableView> views = viewMapper.selectByTableId(tableId);
        return converter.toViewVOList(views);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createView(Long tableId, BitableViewCreateDTO dto, Long userId) {
        // 验证 viewType 合法
        ViewType viewType = ViewType.fromCode(dto.getViewType());
        if (viewType == null) {
            throw new BusinessException("不支持的视图类型: " + dto.getViewType());
        }

        BitableView view = new BitableView();
        BeanUtils.copyProperties(dto, view);
        view.setTableId(tableId);
        view.setCreatedBy(userId);
        view.setSortOrder(0);
        viewMapper.insert(view);

        return view.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateView(Long id, BitableViewUpdateDTO dto) {
        BitableView existing = viewMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("视图不存在");
        }

        // 验证 viewType（如果传了新的 viewType）
        if (dto.getViewType() != null) {
            ViewType viewType = ViewType.fromCode(dto.getViewType());
            if (viewType == null) {
                throw new BusinessException("不支持的视图类型: " + dto.getViewType());
            }
        }

        UpdateWrapper<BitableView> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", id);
        if (dto.getName() != null) {
            wrapper.set("name", dto.getName());
        }
        if (dto.getViewType() != null) {
            wrapper.set("view_type", dto.getViewType());
        }
        if (dto.getSortConfig() != null) {
            wrapper.set("sort_config", dto.getSortConfig());
        }
        if (dto.getFilterConfig() != null) {
            wrapper.set("filter_config", dto.getFilterConfig());
        }
        if (dto.getGroupConfig() != null) {
            wrapper.set("group_config", dto.getGroupConfig());
        }
        if (dto.getColumnConfig() != null) {
            wrapper.set("column_config", dto.getColumnConfig());
        }
        if (dto.getColorConfig() != null) {
            wrapper.set("color_config", dto.getColorConfig());
        }
        if (dto.getSortOrder() != null) {
            wrapper.set("sort_order", dto.getSortOrder());
        }
        viewMapper.update(null, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteView(Long id) {
        BitableView existing = viewMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("视图不存在");
        }
        viewMapper.deleteById(id);
    }
}