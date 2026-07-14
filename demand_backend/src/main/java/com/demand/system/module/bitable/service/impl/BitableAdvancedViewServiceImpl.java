package com.demand.system.module.bitable.service.impl;

import com.demand.system.common.exception.BusinessException;
import com.demand.system.module.bitable.converter.BitableConverter;
import com.demand.system.module.bitable.dto.BitableCellValueVO;
import com.demand.system.module.bitable.dto.BitableFieldVO;
import com.demand.system.module.bitable.dto.BitableRecordVO;
import com.demand.system.module.bitable.dto.CalendarViewData;
import com.demand.system.module.bitable.dto.GalleryViewData;
import com.demand.system.module.bitable.dto.GanttViewData;
import com.demand.system.module.bitable.entity.BitableView;
import com.demand.system.module.bitable.entity.BitableCellValue;
import com.demand.system.module.bitable.entity.BitableRecord;
import com.demand.system.module.bitable.mapper.BitableCellMapper;
import com.demand.system.module.bitable.mapper.BitableFieldMapper;
import com.demand.system.module.bitable.mapper.BitableRecordMapper;
import com.demand.system.module.bitable.mapper.BitableViewMapper;
import com.demand.system.module.bitable.service.BitableAdvancedViewService;
import com.demand.system.common.util.UserNameResolver;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 多维表格-高级视图 Service 实现
 * Phase 4 简单实现: 返回 records + 字段配置
 */
@Service
public class BitableAdvancedViewServiceImpl implements BitableAdvancedViewService {

    private final BitableViewMapper viewMapper;
    private final BitableFieldMapper fieldMapper;
    private final BitableRecordMapper recordMapper;
    private final BitableCellMapper cellMapper;
    private final BitableConverter converter;
    private final UserNameResolver userNameResolver;

    public BitableAdvancedViewServiceImpl(BitableViewMapper viewMapper,
                                          BitableFieldMapper fieldMapper,
                                          BitableRecordMapper recordMapper,
                                          BitableCellMapper cellMapper,
                                          BitableConverter converter,
                                          UserNameResolver userNameResolver) {
        this.viewMapper = viewMapper;
        this.fieldMapper = fieldMapper;
        this.recordMapper = recordMapper;
        this.cellMapper = cellMapper;
        this.converter = converter;
        this.userNameResolver = userNameResolver;
    }

    @Override
    public GanttViewData getGanttView(Long viewId, Long tableId) {
        BitableView view = viewMapper.selectById(viewId);
        if (view == null) {
            throw new BusinessException("视图不存在");
        }

        List<BitableFieldVO> fields = converter.toFieldVOList(fieldMapper.selectByTableId(tableId));
        List<BitableRecordVO> records = loadRecordsWithCells(tableId);

        GanttViewData data = new GanttViewData();
        data.setViewId(viewId);
        data.setTableId(tableId);
        data.setViewName(view.getName());
        data.setFields(fields);
        data.setRecords(records);
        return data;
    }

    @Override
    public CalendarViewData getCalendarView(Long viewId, Long tableId) {
        BitableView view = viewMapper.selectById(viewId);
        if (view == null) {
            throw new BusinessException("视图不存在");
        }

        List<BitableFieldVO> fields = converter.toFieldVOList(fieldMapper.selectByTableId(tableId));
        List<BitableRecordVO> records = loadRecordsWithCells(tableId);

        CalendarViewData data = new CalendarViewData();
        data.setViewId(viewId);
        data.setTableId(tableId);
        data.setViewName(view.getName());
        data.setFields(fields);
        data.setRecords(records);
        return data;
    }

    @Override
    public GalleryViewData getGalleryView(Long viewId, Long tableId) {
        BitableView view = viewMapper.selectById(viewId);
        if (view == null) {
            throw new BusinessException("视图不存在");
        }

        List<BitableFieldVO> fields = converter.toFieldVOList(fieldMapper.selectByTableId(tableId));
        List<BitableRecordVO> records = loadRecordsWithCells(tableId);

        GalleryViewData data = new GalleryViewData();
        data.setViewId(viewId);
        data.setTableId(tableId);
        data.setViewName(view.getName());
        data.setFields(fields);
        data.setRecords(records);
        return data;
    }

    /**
     * 加载表的所有记录及其单元格值
     */
    private List<BitableRecordVO> loadRecordsWithCells(Long tableId) {
        int total = recordMapper.countByTableId(tableId);
        int limit = Math.min(total, 1000);
        List<BitableRecord> records = recordMapper.selectByTableId(tableId, 0, limit);
        List<BitableRecordVO> voList = converter.toRecordVOList(records);

        if (!records.isEmpty()) {
            List<Long> recordIds = records.stream().map(BitableRecord::getId).collect(Collectors.toList());
            List<BitableCellValue> cells = cellMapper.selectByRecordIds(recordIds);
            List<BitableCellValueVO> cellVOs = converter.toCellValueVOList(cells);

            Map<Long, Map<Long, BitableCellValueVO>> recordCellsMap = new java.util.LinkedHashMap<>();
            for (BitableCellValueVO cellVO : cellVOs) {
                recordCellsMap.computeIfAbsent(cellVO.getRecordId(), k -> new java.util.LinkedHashMap<>())
                        .put(cellVO.getFieldId(), cellVO);
            }

            for (BitableRecordVO vo : voList) {
                vo.setCells(recordCellsMap.getOrDefault(vo.getId(), new java.util.LinkedHashMap<>()));
                vo.setCreatedByName(userNameResolver.resolveUserName(vo.getCreatedBy(), "未知用户"));
                vo.setUpdatedByName(userNameResolver.resolveUserName(vo.getUpdatedBy(), "未知用户"));
            }
        }

        return voList;
    }
}
