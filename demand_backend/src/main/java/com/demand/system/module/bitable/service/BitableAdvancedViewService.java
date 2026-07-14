package com.demand.system.module.bitable.service;

import com.demand.system.module.bitable.dto.GanttViewData;
import com.demand.system.module.bitable.dto.CalendarViewData;
import com.demand.system.module.bitable.dto.GalleryViewData;

/**
 * 多维表格-高级视图 Service
 */
public interface BitableAdvancedViewService {

    /**
     * 获取甘特视图数据
     *
     * @param viewId  视图ID
     * @param tableId 数据表ID
     * @return 甘特视图数据
     */
    GanttViewData getGanttView(Long viewId, Long tableId);

    /**
     * 获取日历视图数据
     *
     * @param viewId  视图ID
     * @param tableId 数据表ID
     * @return 日历视图数据
     */
    CalendarViewData getCalendarView(Long viewId, Long tableId);

    /**
     * 获取画廊视图数据
     *
     * @param viewId  视图ID
     * @param tableId 数据表ID
     * @return 画廊视图数据
     */
    GalleryViewData getGalleryView(Long viewId, Long tableId);
}
