package com.demand.system.module.bitable.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.demand.system.common.exception.BusinessException;
import com.demand.system.module.bitable.constant.ViewType;
import com.demand.system.module.bitable.converter.BitableConverter;
import com.demand.system.module.bitable.dto.BitableViewCreateDTO;
import com.demand.system.module.bitable.dto.BitableViewUpdateDTO;
import com.demand.system.module.bitable.dto.BitableViewVO;
import com.demand.system.module.bitable.entity.BitableTable;
import com.demand.system.module.bitable.entity.BitableView;
import com.demand.system.module.bitable.mapper.BitableTableMapper;
import com.demand.system.module.bitable.mapper.BitableViewMapper;
import com.demand.system.module.bitable.service.BitableViewService;
import com.demand.system.module.bitable.util.BitableJsonUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 多维表格-视图定义 Service 实现
 */
@Service
public class BitableViewServiceImpl implements BitableViewService {

    private final BitableViewMapper viewMapper;
    private final BitableTableMapper tableMapper;
    private final BitableConverter converter;

    public BitableViewServiceImpl(BitableViewMapper viewMapper,
                                  BitableTableMapper tableMapper,
                                  BitableConverter converter) {
        this.viewMapper = viewMapper;
        this.tableMapper = tableMapper;
        this.converter = converter;
    }

    @Override
    public List<BitableViewVO> listViews(Long tableId) {
        List<BitableView> views = viewMapper.selectByTableId(tableId);
        // 获取表的默认视图ID
        BitableTable table = tableMapper.selectById(tableId);
        Long defaultViewId = table != null ? table.getDefaultViewId() : null;

        return views.stream().map(view -> {
            BitableViewVO vo = toViewVO(view);
            vo.setIsDefault(view.getId().equals(defaultViewId));
            return vo;
        }).toList();
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
        view.setName(dto.getName());
        view.setViewType(dto.getViewType());
        view.setSortConfig(BitableJsonUtils.toJsonString(dto.getSortConfig()));
        view.setFilterConfig(BitableJsonUtils.toJsonString(dto.getFilterConfig()));
        view.setGroupConfig(BitableJsonUtils.toJsonString(dto.getGroupConfig()));
        view.setColumnConfig(BitableJsonUtils.toJsonString(dto.getColumnConfig()));
        view.setColorConfig(BitableJsonUtils.toJsonString(dto.getColorConfig()));
        view.setConfig(BitableJsonUtils.toJsonString(dto.getConfig()));
        view.setTableId(tableId);
        view.setCreatedBy(userId);
        view.setSortOrder(0);
        view.setVersion(0);
        viewMapper.insert(view);

        // 如果是表的第一个视图，自动设为默认视图
        Long viewCount = viewMapper.selectCount(new LambdaQueryWrapper<BitableView>()
                .eq(BitableView::getTableId, tableId));
        if (viewCount == 1) {
            setDefaultView(tableId, view.getId());
        }

        return view.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateView(Long id, BitableViewUpdateDTO dto) {
        BitableView existing = viewMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("视图不存在");
        }

        // 乐观锁校验：如果传了 version，则必须匹配
        if (dto.getVersion() != null && !dto.getVersion().equals(existing.getVersion())) {
            throw new BusinessException("视图已被他人修改，请刷新后重试");
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
        // 乐观锁条件
        wrapper.eq("version", existing.getVersion());

        if (dto.getName() != null) {
            wrapper.set("name", dto.getName());
        }
        if (dto.getViewType() != null) {
            wrapper.set("view_type", dto.getViewType());
        }
        if (dto.getSortConfig() != null) {
            wrapper.set("sort_config", BitableJsonUtils.toJsonString(dto.getSortConfig()));
        }
        if (dto.getFilterConfig() != null) {
            wrapper.set("filter_config", BitableJsonUtils.toJsonString(dto.getFilterConfig()));
        }
        if (dto.getGroupConfig() != null) {
            wrapper.set("group_config", BitableJsonUtils.toJsonString(dto.getGroupConfig()));
        }
        if (dto.getColumnConfig() != null) {
            wrapper.set("column_config", BitableJsonUtils.toJsonString(dto.getColumnConfig()));
        }
        if (dto.getColorConfig() != null) {
            wrapper.set("color_config", BitableJsonUtils.toJsonString(dto.getColorConfig()));
        }
        if (dto.getConfig() != null) {
            wrapper.set("config", BitableJsonUtils.toJsonString(dto.getConfig()));
        }
        if (dto.getSortOrder() != null) {
            wrapper.set("sort_order", dto.getSortOrder());
        }
        // 版本号自增
        wrapper.set("version", existing.getVersion() + 1);

        int rows = viewMapper.update(null, wrapper);
        if (rows == 0) {
            throw new BusinessException("视图已被他人修改，请刷新后重试");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteView(Long id) {
        BitableView existing = viewMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("视图不存在");
        }

        // 检查是否为默认视图
        BitableTable table = tableMapper.selectById(existing.getTableId());
        if (table != null && id.equals(table.getDefaultViewId())) {
            throw new BusinessException("不能删除默认视图，请先设置其他视图为默认视图");
        }

        // 检查是否为最后一个视图
        Long viewCount = viewMapper.selectCount(new LambdaQueryWrapper<BitableView>()
                .eq(BitableView::getTableId, existing.getTableId()));
        if (viewCount <= 1) {
            throw new BusinessException("不能删除最后一个视图");
        }

        viewMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long duplicateView(Long viewId, Long userId) {
        BitableView source = viewMapper.selectById(viewId);
        if (source == null) {
            throw new BusinessException("源视图不存在");
        }

        BitableView copy = new BitableView();
        copy.setTableId(source.getTableId());
        copy.setName(source.getName() + "_副本");
        copy.setViewType(source.getViewType());
        copy.setSortConfig(source.getSortConfig());
        copy.setFilterConfig(source.getFilterConfig());
        copy.setGroupConfig(source.getGroupConfig());
        copy.setColumnConfig(source.getColumnConfig());
        copy.setColorConfig(source.getColorConfig());
        copy.setConfig(source.getConfig());
        copy.setSortOrder(source.getSortOrder() + 1);
        copy.setVersion(0);
        copy.setCreatedBy(userId);
        viewMapper.insert(copy);

        return copy.getId();
    }

    @Override
    public BitableViewVO getViewById(Long viewId) {
        BitableView view = viewMapper.selectById(viewId);
        if (view == null) {
            throw new BusinessException("视图不存在");
        }
        return toViewVO(view);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setDefaultView(Long tableId, Long viewId) {
        // 验证视图属于该表
        BitableView view = viewMapper.selectById(viewId);
        if (view == null || !view.getTableId().equals(tableId)) {
            throw new BusinessException("视图不存在或不属于该数据表");
        }

        UpdateWrapper<BitableTable> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", tableId)
               .set("default_view_id", viewId);
        tableMapper.update(null, wrapper);
    }

    private BitableViewVO toViewVO(BitableView view) {
        BitableViewVO vo = new BitableViewVO();
        vo.setId(view.getId());
        vo.setTableId(view.getTableId());
        vo.setName(view.getName());
        vo.setViewType(view.getViewType());
        vo.setSortConfig(BitableJsonUtils.parseJson(view.getSortConfig()));
        vo.setFilterConfig(BitableJsonUtils.parseJson(view.getFilterConfig()));
        vo.setGroupConfig(BitableJsonUtils.parseJson(view.getGroupConfig()));
        vo.setColumnConfig(BitableJsonUtils.parseJson(view.getColumnConfig()));
        vo.setColorConfig(BitableJsonUtils.parseJson(view.getColorConfig()));
        vo.setConfig(BitableJsonUtils.parseJson(view.getConfig()));
        vo.setSortOrder(view.getSortOrder());
        vo.setVersion(view.getVersion());
        vo.setCreatedBy(view.getCreatedBy());
        vo.setCreatedAt(view.getCreatedAt());
        vo.setUpdatedAt(view.getUpdatedAt());
        return vo;
    }
}
