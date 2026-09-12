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
        BitableView view = requireViewOfTable(viewId, tableId);
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
        BitableView view = requireViewOfTable(viewId, tableId);
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
        BitableView view = requireViewOfTable(viewId, tableId);
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
     * 校验视图存在且确实属于指定数据表，防止用他表 viewId 读取任意 tableId 的数据
     */
    private BitableView requireViewOfTable(Long viewId, Long tableId) {
        BitableView view = viewMapper.selectById(viewId);
        if (view == null || tableId == null || !tableId.equals(view.getTableId())) {
            throw new BusinessException("视图不存在或不属于当前数据表");
        }
        return view;
    }

    /** 高级视图单次加载行数硬上限（防病态数据量拖垮内存），循环分页加载 */
    private static final int MAX_VIEW_ROWS = 20_000;
    private static final int VIEW_PAGE_SIZE = 1000;

    /**
     * 分页加载表的所有记录及其单元格值（不再静默截断 1000 行）
     */
    private List<BitableRecordVO> loadRecordsWithCells(Long tableId) {
        List<BitableRecord> records = new java.util.ArrayList<>();
        int total = recordMapper.countByTableId(tableId);
        int limit = Math.min(total, MAX_VIEW_ROWS);
        for (int offset = 0; offset < limit; offset += VIEW_PAGE_SIZE) {
            records.addAll(recordMapper.selectByTableId(tableId, offset, VIEW_PAGE_SIZE));
            if (records.size() >= limit) {
                break;
            }
        }
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
